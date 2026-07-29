from __future__ import annotations

import asyncio
import logging
import os
import signal
import socket
from datetime import UTC, datetime, timedelta
from typing import Any, cast
from uuid import UUID, uuid4

import asyncpg  # type: ignore[import-untyped]

from app.clients.delegation_client import (
    DelegationClient,
    DelegationClientError,
    KnowledgeCoreDelegationClient,
)
from app.clients.mcp_client import McpClientError, OfficialMcpClient, ReviewMcpClient
from app.config import Settings, get_settings
from app.graph.document_sync_workflow import DocumentSyncWorkflow, ReviewSubmissionError
from app.graph.state import AgentState
from app.persistence.job_repository import AgentJobRepository, PostgresAgentJobRepository
from app.planning.deepseek_unit_planner import DeepSeekUnitPlanner
from app.planning.unit_plan_validator import UnitPlanValidationError
from app.planning.validator import PlanValidationError
from app.providers.base import ModelProvider, ModelProviderError
from app.providers.deepseek import DeepSeekProvider
from app.runtime.delegated_mcp_client import DelegatedMcpClient
from app.runtime.job_executor import JobExecutionError
from app.runtime.project_discovery import ProjectDiscoveryService
from app.runtime.project_unit_context import ProjectUnitContextBuilder
from app.runtime.semantic_planner import materialize_deepseek_units, overlapping_file_count

LOGGER = logging.getLogger("devcollab.agent.worker")
RETRYABLE_ERRORS = {
    "MODEL_TIMEOUT",
    "MODEL_UNAVAILABLE",
    "MCP_UNAVAILABLE",
    "DATABASE_UNAVAILABLE",
}
PHASES = {
    "PLANNING": "MODEL_RUNNING",
    "VALIDATING": "VALIDATING",
    "REPAIRING_PLAN": "REPAIRING",
    "SUBMITTING_REVIEW": "SUBMITTING_REVIEW",
}


class AgentWorker:
    def __init__(
        self,
        repository: AgentJobRepository,
        mcp_client: ReviewMcpClient,
        delegation_client: DelegationClient,
        provider: ModelProvider,
        settings: Settings,
        *,
        worker_id: str | None = None,
    ) -> None:
        self._repository = repository
        self._mcp_client = mcp_client
        self._delegation_client = delegation_client
        self._provider = provider
        self._settings = settings
        self._worker_id = (
            worker_id
            or f"{socket.gethostname()}-{os.getpid()}-{uuid4().hex[:12]}"
        )
        self._stopping = asyncio.Event()

    def stop(self) -> None:
        self._stopping.set()

    async def run(self) -> None:
        LOGGER.info("Agent worker started workerId=%s", self._worker_id)
        tasks: set[asyncio.Task[None]] = set()
        while not self._stopping.is_set():
            try:
                await self._repository.record_worker_heartbeat(self._worker_id)
                finished = {task for task in tasks if task.done()}
                for task in finished:
                    tasks.remove(task)
                    if task.exception() is not None:
                        LOGGER.error(
                            "Agent worker task failed",
                            exc_info=task.exception(),
                        )
                if len(tasks) >= self._settings.agent_project_unit_concurrency:
                    await asyncio.wait(tasks, return_when=asyncio.FIRST_COMPLETED)
                    continue
                unit = await self._repository.claim_next_unit(
                    self._worker_id, self._settings.agent_unit_lease_seconds
                )
                if unit is None:
                    if tasks:
                        await asyncio.wait(
                            tasks,
                            timeout=self._settings.agent_worker_poll_seconds,
                            return_when=asyncio.FIRST_COMPLETED,
                        )
                        continue
                    await self._wait_for_poll()
                    continue
                tasks.add(
                    asyncio.create_task(
                        self._execute(unit), name=f"agent-unit-{unit['id']}"
                    )
                )
            except asyncio.CancelledError:
                raise
            except Exception:
                LOGGER.exception("Agent worker loop failed")
                await self._wait_for_poll()
        if tasks:
            await asyncio.gather(*tasks, return_exceptions=True)

    async def _wait_for_poll(self) -> None:
        try:
            await asyncio.wait_for(
                self._stopping.wait(), timeout=self._settings.agent_worker_poll_seconds
            )
        except TimeoutError:
            pass

    async def _execute(self, unit: dict[str, Any]) -> None:
        job = unit["job"]
        unit_id = UUID(str(unit["id"]))
        job_id = UUID(str(job["id"]))
        heartbeat = asyncio.create_task(
            self._heartbeat(unit_id), name=f"heartbeat-{unit_id}"
        )
        delegated = DelegatedMcpClient(
            self._mcp_client,
            self._delegation_client,
            delegation_id=UUID(str(job["delegation_id"])),
            job_id=job_id,
            revision=str(job["revision"]),
            knowledge_core_base_url=self._settings.knowledge_core_base_url,
            request_timeout_seconds=self._settings.agent_request_timeout_seconds,
        )

        async def on_status(status: str, _node: str, _updates: dict[str, Any]) -> None:
            phase = PHASES.get(status)
            if phase:
                updated = await self._repository.update_phase(
                    unit_id, self._worker_id, phase
                )
                if not updated:
                    raise RuntimeError("Agent unit lease was lost")

        async def run_workflow() -> dict[str, Any]:
            workflow = DocumentSyncWorkflow(
                delegated, self._provider, self._settings, on_status
            )
            scope = job["scope_payload"]
            selected_paths: list[str]
            preferred_document_ids: list[str] = []
            user_instruction = job.get("user_instruction")
            if unit.get("unit_kind") == "SEMANTIC_ANALYSIS":
                context = await self._repository.get_unit_context(unit_id)
                selected_paths = [
                    str(item.get("file_path") or item.get("filePath"))
                    for item in context.get("files", [])
                ]
                preferred_document_ids = [
                    str(item.get("document_id") or item.get("documentId"))
                    for item in context.get("documents", [])
                ]
                unit_instruction = (
                    f"项目语义模块：{context.get('display_name')}\n"
                    f"模块职责：{context.get('summary') or ''}"
                )
                user_instruction = (
                    f"{user_instruction}\n{unit_instruction}"
                    if user_instruction
                    else unit_instruction
                )
            else:
                selected_paths = [str(scope["filePath"])]
            run_id = f"job-{job_id}-unit-{unit_id}"
            initial_state: AgentState = {
                "run_id": run_id,
                "workspace_id": str(job["workspace_id"]),
                "repository_id": str(job["repository_id"]),
                "revision": str(job["revision"]),
                "selected_paths": selected_paths,
                "preferred_document_ids": preferred_document_ids,
                "user_instruction": user_instruction,
                "authorization": "delegated",
                "tool_call_count": 0,
                "code_chars_used": 0,
                "trace_events": [],
                "errors": [],
            }
            if unit.get("unit_kind") == "SEMANTIC_ANALYSIS":
                initial_state = await ProjectUnitContextBuilder(
                    delegated, self._settings
                ).build(
                    run_id=run_id,
                    workspace_id=str(job["workspace_id"]),
                    repository_id=str(job["repository_id"]),
                    revision=str(job["revision"]),
                    selected_paths=selected_paths,
                    preferred_document_ids=preferred_document_ids,
                    user_instruction=user_instruction,
                )
                return await workflow.execute_context_bundle(initial_state)
            return await workflow.graph.ainvoke(initial_state)

        async def run_project_discovery() -> None:
            async def on_phase(phase: str) -> None:
                updated = await self._repository.update_phase(
                    unit_id, self._worker_id, phase
                )
                if not updated:
                    raise RuntimeError("Agent unit lease was lost")

            service = ProjectDiscoveryService(delegated, self._settings, on_phase)
            (
                files,
                project_files,
                planner_batches,
                project_index,
                stats,
            ) = await service.execute(
                job_id=job_id,
                workspace_id=UUID(str(job["workspace_id"])),
                repository_id=UUID(str(job["repository_id"])),
                revision=str(job["revision"]),
            )
            execution_limit = self._settings.agent_project_execution_limit
            planner_limit = self._settings.agent_max_analysis_units
            project_index["requestedMaxUnits"] = planner_limit
            for batch in planner_batches:
                batch["requestedMaxUnits"] = planner_limit
                batch["constraints"] = {
                    "maxUnits": planner_limit,
                    "maxFilesPerUnit": (
                        self._settings.agent_max_total_files_per_unit
                    ),
                }
            planner = DeepSeekUnitPlanner(
                self._provider,
                max_files_per_unit=self._settings.agent_max_total_files_per_unit,
                max_units=planner_limit,
                on_phase=on_phase,
            )
            unit_plan = (
                await planner.plan(planner_batches[0])
                if len(planner_batches) == 1
                else await planner.plan(
                    planner_batches,
                    validation_index=project_index,
                )
            )
            units = materialize_deepseek_units(
                unit_plan,
                project_files,
                job_id=job_id,
                revision=str(job["revision"]),
            )
            stats["analysis_unit_count"] = len(units)
            stats["overlapping_file_count"] = overlapping_file_count(units)
            await self._repository.complete_project_discovery(
                unit_id,
                self._worker_id,
                files,
                units,
                stats,
                self._settings.agent_unit_max_attempts,
                execution_limit,
            )

        workflow_task: asyncio.Task[dict[str, Any]] | None = None
        discovery_task: asyncio.Task[None] | None = None
        try:
            if unit.get("unit_kind") == "PROJECT_DISCOVERY":
                discovery_task = asyncio.create_task(
                    run_project_discovery(), name=f"project-discovery-{unit_id}"
                )
                async with asyncio.timeout(self._settings.agent_unit_timeout_seconds):
                    done, _pending = await asyncio.wait(
                        {
                            cast(asyncio.Task[Any], discovery_task),
                            cast(asyncio.Task[Any], heartbeat),
                        },
                        return_when=asyncio.FIRST_COMPLETED,
                    )
                    if heartbeat in done:
                        discovery_task.cancel()
                        await asyncio.gather(discovery_task, return_exceptions=True)
                        heartbeat.result()
                        raise RuntimeError("Agent unit heartbeat stopped unexpectedly")
                    discovery_task.result()
                return
            workflow_task = asyncio.create_task(
                run_workflow(), name=f"workflow-{unit_id}"
            )
            async with asyncio.timeout(self._settings.agent_unit_timeout_seconds):
                done, _pending = await asyncio.wait(
                    {workflow_task, heartbeat},
                    return_when=asyncio.FIRST_COMPLETED,
                )
                if heartbeat in done:
                    workflow_task.cancel()
                    await asyncio.gather(workflow_task, return_exceptions=True)
                    heartbeat.result()
                    raise RuntimeError("Agent unit heartbeat stopped unexpectedly")
                result = workflow_task.result()
            review_id_value = result.get("change_request_id")
            review_id = UUID(str(review_id_value)) if review_id_value else None
            outcome = "REVIEW_SUBMITTED" if review_id else "NO_CHANGE"
            await self._repository.complete_unit(
                unit_id, self._worker_id, outcome, review_id
            )
        except Exception as exc:
            LOGGER.exception(
                "Agent unit execution failed jobId=%s unitId=%s unitKind=%s",
                job_id,
                unit_id,
                unit.get("unit_kind"),
            )
            if unit.get("unit_kind") == "PROJECT_DISCOVERY" and isinstance(
                exc, TimeoutError
            ):
                code, message = (
                    "PROJECT_DISCOVERY_TIMEOUT",
                    "Project discovery exceeded the configured unit timeout",
                )
            else:
                code, message = self._safe_error(exc)
            attempt = int(unit["attempt"])
            max_attempts = int(unit["max_attempts"])
            retry_at = None
            if code in RETRYABLE_ERRORS and attempt < max_attempts:
                delay = 60 if attempt == 1 else 300
                retry_at = datetime.now(UTC) + timedelta(seconds=delay)
            await self._repository.fail_unit(
                unit_id, self._worker_id, code, message, retry_at
            )
        finally:
            if discovery_task is not None and not discovery_task.done():
                discovery_task.cancel()
                await asyncio.gather(discovery_task, return_exceptions=True)
            if workflow_task is not None and not workflow_task.done():
                workflow_task.cancel()
                await asyncio.gather(workflow_task, return_exceptions=True)
            heartbeat.cancel()
            await asyncio.gather(heartbeat, return_exceptions=True)

    async def _heartbeat(self, unit_id: UUID) -> None:
        while True:
            await asyncio.sleep(self._settings.agent_worker_heartbeat_seconds)
            await self._repository.record_worker_heartbeat(self._worker_id)
            renewed = await self._repository.heartbeat(
                unit_id, self._worker_id, self._settings.agent_unit_lease_seconds
            )
            if not renewed:
                raise RuntimeError("Agent unit lease was lost")

    @staticmethod
    def _safe_error(exc: Exception) -> tuple[str, str]:
        if isinstance(exc, TimeoutError):
            return "MODEL_TIMEOUT", "Agent unit timed out"
        if isinstance(exc, ModelProviderError):
            return exc.code, str(exc)[:300]
        if isinstance(exc, PlanValidationError):
            return "PLAN_VALIDATION_FAILED", "Agent plan failed validation after one repair"
        if isinstance(exc, UnitPlanValidationError):
            return (
                "UNIT_PLAN_VALIDATION_FAILED",
                str(exc)[:300],
            )
        if isinstance(exc, ReviewSubmissionError):
            return "REVIEW_CONFLICT", str(exc)[:300]
        if isinstance(exc, DelegationClientError):
            return exc.code, str(exc)[:300]
        if isinstance(exc, McpClientError):
            return exc.code, str(exc)[:300]
        if isinstance(exc, JobExecutionError):
            return exc.code, str(exc)[:300]
        if isinstance(exc, ValueError) and str(exc) == "UNIT_LIMIT_EXCEEDED":
            return "UNIT_LIMIT_EXCEEDED", "Analysis unit limit exceeded"
        if isinstance(
            exc,
            (
                asyncpg.PostgresConnectionError,
                asyncpg.CannotConnectNowError,
                asyncpg.ConnectionDoesNotExistError,
                asyncpg.InterfaceError,
                ConnectionError,
            ),
        ):
            return "DATABASE_UNAVAILABLE", "Agent database is temporarily unavailable"
        return "INTERNAL_ERROR", "Agent unit failed"


async def main() -> None:
    settings = get_settings()
    logging.basicConfig(level=logging.INFO)
    repository = await PostgresAgentJobRepository.connect(settings.agent_database_url)
    worker = AgentWorker(
        repository,
        OfficialMcpClient(settings.mcp_base_url, settings.agent_request_timeout_seconds),
        KnowledgeCoreDelegationClient(
            settings.knowledge_core_base_url,
            settings.agent_internal_service_token,
            settings.agent_delegation_timeout_seconds,
        ),
        DeepSeekProvider(
            api_key=settings.deepseek_api_key,
            base_url=settings.deepseek_base_url,
            model=settings.deepseek_model,
            connect_timeout_seconds=settings.agent_model_connect_timeout_seconds,
            request_timeout_seconds=settings.agent_model_request_timeout_seconds,
        ),
        settings,
    )
    loop = asyncio.get_running_loop()
    for name in ("SIGINT", "SIGTERM"):
        if hasattr(signal, name):
            loop.add_signal_handler(getattr(signal, name), worker.stop)
    try:
        await worker.run()
    finally:
        await repository.close()


if __name__ == "__main__":
    asyncio.run(main())
