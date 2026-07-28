from __future__ import annotations

import asyncio
import logging
import os
import signal
import socket
from datetime import UTC, datetime, timedelta
from typing import Any
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
from app.planning.validator import PlanValidationError
from app.providers.base import ModelProvider, ModelProviderError
from app.providers.deepseek import DeepSeekProvider
from app.runtime.delegated_mcp_client import DelegatedMcpClient

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
        while not self._stopping.is_set():
            try:
                await self._repository.record_worker_heartbeat(self._worker_id)
                unit = await self._repository.claim_next_unit(
                    self._worker_id, self._settings.agent_unit_lease_seconds
                )
                if unit is None:
                    await self._wait_for_poll()
                    continue
                await self._execute(unit)
            except asyncio.CancelledError:
                raise
            except Exception:
                LOGGER.exception("Agent worker loop failed")
                await self._wait_for_poll()

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
            run_id = f"job-{job_id}-unit-{unit_id}"
            initial_state: AgentState = {
                "run_id": run_id,
                "workspace_id": str(job["workspace_id"]),
                "repository_id": str(job["repository_id"]),
                "selected_paths": [str(scope["filePath"])],
                "user_instruction": job.get("user_instruction"),
                "authorization": "delegated",
                "tool_call_count": 0,
                "code_chars_used": 0,
                "trace_events": [],
                "errors": [],
            }
            return await workflow.graph.ainvoke(initial_state)

        workflow_task: asyncio.Task[dict[str, Any]] | None = None
        try:
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
        if isinstance(exc, ReviewSubmissionError):
            return "REVIEW_CONFLICT", str(exc)[:300]
        if isinstance(exc, DelegationClientError):
            return exc.code, str(exc)[:300]
        if isinstance(exc, McpClientError):
            return exc.code, str(exc)[:300]
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
