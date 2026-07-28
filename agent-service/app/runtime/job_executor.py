import asyncio
from collections import Counter
from datetime import UTC, datetime
from pathlib import PurePosixPath
from typing import Any

from app.clients.mcp_client import McpClientError, ReadOnlyMcpClient
from app.clients.run_store import RunStore
from app.config import Settings
from app.runtime.file_classification import classify_file
from app.runtime.unit_grouping import build_analysis_units


class AgentJobExecutor:
    def __init__(
        self,
        client: ReadOnlyMcpClient,
        store: RunStore,
        settings: Settings,
    ) -> None:
        self._client = client
        self._store = store
        self._settings = settings
        self._tasks: dict[str, asyncio.Task[None]] = {}

    def start(
        self,
        *,
        job_id: str,
        workspace_id: str,
        repository_id: str,
        scope: dict[str, Any],
        authorization: str,
        created_at: str,
    ) -> None:
        existing = self._tasks.get(job_id)
        if existing and not existing.done():
            return
        task = asyncio.create_task(
            self._execute(
                job_id=job_id,
                workspace_id=workspace_id,
                repository_id=repository_id,
                scope=scope,
                authorization=authorization,
                created_at=created_at,
            ),
            name=f"agent-job-{job_id}",
        )
        self._tasks[job_id] = task
        task.add_done_callback(lambda _: self._tasks.pop(job_id, None))

    async def close(self) -> None:
        tasks = list(self._tasks.values())
        for task in tasks:
            task.cancel()
        if tasks:
            await asyncio.gather(*tasks, return_exceptions=True)

    async def _execute(
        self,
        *,
        job_id: str,
        workspace_id: str,
        repository_id: str,
        scope: dict[str, Any],
        authorization: str,
        created_at: str,
    ) -> None:
        record: dict[str, Any] = {
            "jobId": job_id,
            "status": "DISCOVERING_FILES",
            "workspaceId": workspace_id,
            "repositoryId": repository_id,
            "scope": scope,
            "discoveredFileCount": 0,
            "eligibleCodeCount": 0,
            "skippedFileCount": 0,
            "skippedReasonCounts": {},
            "unitCount": 0,
            "completedUnitCount": 0,
            "reviewRequestIds": [],
            "units": [],
            "errorCode": None,
            "errorMessage": None,
            "createdAt": created_at,
            "updatedAt": self._now(),
        }
        try:
            await self._save(record)
            discovered = await self._discover(workspace_id, repository_id, scope, authorization)
            record["discoveredFileCount"] = len(discovered)
            record["status"] = "CLASSIFYING_FILES"
            record["updatedAt"] = self._now()
            await self._save(record)

            classified = [
                classify_file(
                    item,
                    max_size_bytes=self._settings.agent_max_single_file_bytes,
                    deleted=item.get("status") == "DELETED",
                )
                for item in discovered
            ]
            eligible = [item for item in classified if item.eligible]
            skipped = [item for item in classified if not item.eligible]
            record["eligibleCodeCount"] = len(eligible)
            record["skippedFileCount"] = len(skipped)
            record["skippedReasonCounts"] = dict(
                sorted(Counter(item.classification for item in skipped).items())
            )
            record["status"] = "LOADING_BINDINGS"
            record["updatedAt"] = self._now()
            await self._save(record)

            bindings = await self._load_bindings(
                workspace_id,
                repository_id,
                [item.file_path for item in eligible],
                authorization,
            )
            record["status"] = "BUILDING_UNITS"
            record["updatedAt"] = self._now()
            await self._save(record)
            units = build_analysis_units(
                eligible,
                bindings,
                source_type=str(scope["type"]),
                max_files=self._settings.agent_max_files_per_unit,
                max_deleted=self._settings.agent_max_deleted_paths_per_unit,
                max_units=self._settings.agent_max_units,
            )
            record["units"] = [unit.model_dump(mode="json") for unit in units]
            record["unitCount"] = len(units)
            record["status"] = "READY_FOR_ANALYSIS"
            record["updatedAt"] = self._now()
            await self._save(record)
        except asyncio.CancelledError:
            record["status"] = "CANCELLED"
            record["errorCode"] = "INTERNAL_ERROR"
            record["errorMessage"] = "Agent service stopped before the job completed"
            record["updatedAt"] = self._now()
            await self._save(record)
            raise
        except Exception as exc:
            code, message = self._safe_error(exc)
            record["status"] = "FAILED"
            record["errorCode"] = code
            record["errorMessage"] = message
            record["updatedAt"] = self._now()
            await self._save(record)

    async def _discover(
        self,
        workspace_id: str,
        repository_id: str,
        scope: dict[str, Any],
        authorization: str,
    ) -> list[dict[str, Any]]:
        scope_type = scope["type"]
        if scope_type == "GIT_CHANGES":
            return await self._paginate(
                "devcollab.repository.list_changes",
                {"workspaceId": workspace_id, "repositoryId": repository_id},
                authorization,
            )
        if scope_type == "CURRENT_FILE":
            file_path = str(scope["filePath"]).replace("\\", "/")
            parent = PurePosixPath(file_path).parent.as_posix()
            result = await self._paginate(
                "devcollab.repository.list_files",
                {
                    "workspaceId": workspace_id,
                    "repositoryId": repository_id,
                    "pathPrefix": "" if parent == "." else parent,
                    "recursive": False,
                },
                authorization,
            )
            exact = [item for item in result if item.get("filePath") == file_path]
            if not exact:
                raise JobExecutionError(
                    "REPOSITORY_FILE_NOT_FOUND", "Repository file was not found"
                )
            return exact
        prefix = "" if scope_type == "PROJECT_INITIALIZATION" else str(scope["pathPrefix"])
        result = await self._paginate(
            "devcollab.repository.list_files",
            {
                "workspaceId": workspace_id,
                "repositoryId": repository_id,
                "pathPrefix": prefix,
                "recursive": bool(scope.get("recursive", True)),
            },
            authorization,
        )
        if scope_type == "DIRECTORY" and not result:
            raise JobExecutionError(
                "REPOSITORY_SCOPE_NOT_FOUND", "Repository scope contains no files"
            )
        return result

    async def _paginate(
        self,
        tool: str,
        arguments: dict[str, Any],
        authorization: str,
    ) -> list[dict[str, Any]]:
        cursor: str | None = None
        seen_cursors: set[str] = set()
        unique: dict[str, dict[str, Any]] = {}
        for _ in range(self._settings.agent_max_discovery_pages):
            payload = dict(arguments)
            payload["limit"] = self._settings.agent_repository_page_size
            if cursor:
                payload["cursor"] = cursor
            response = await self._client.call_tool(tool, payload, authorization)
            for item in response.get("files", []):
                path = str(item.get("filePath", "")).replace("\\", "/")
                if path:
                    unique[path] = dict(item)
            if len(unique) > self._settings.agent_max_discovered_files:
                raise JobExecutionError(
                    "DISCOVERY_LIMIT_EXCEEDED",
                    "Repository scope exceeds the configured file limit",
                )
            if not response.get("hasMore"):
                return [unique[path] for path in sorted(unique)]
            next_cursor = response.get("nextCursor")
            if not isinstance(next_cursor, str) or not next_cursor or next_cursor in seen_cursors:
                raise JobExecutionError(
                    "REPOSITORY_SCAN_FAILED", "Repository pagination cursor did not advance"
                )
            seen_cursors.add(next_cursor)
            cursor = next_cursor
        raise JobExecutionError(
            "DISCOVERY_LIMIT_EXCEEDED", "Repository discovery page limit exceeded"
        )

    async def _load_bindings(
        self,
        workspace_id: str,
        repository_id: str,
        paths: list[str],
        authorization: str,
    ) -> dict[str, list[dict[str, Any]]]:
        result: dict[str, list[dict[str, Any]]] = {path: [] for path in paths}
        size = self._settings.agent_binding_batch_size
        try:
            for index in range(0, len(paths), size):
                chunk = paths[index : index + size]
                response = await self._client.call_tool(
                    "devcollab.binding.list_batch",
                    {
                        "workspaceId": workspace_id,
                        "repositoryId": repository_id,
                        "filePaths": chunk,
                    },
                    authorization,
                )
                for group in response.get("files", []):
                    result[str(group["filePath"])] = list(group.get("bindings", []))
        except McpClientError as exc:
            raise JobExecutionError("BINDING_BATCH_FAILED", str(exc)[:300]) from exc
        return result

    async def _save(self, record: dict[str, Any]) -> None:
        await self._store.save_job(
            str(record["jobId"]),
            dict(record),
            self._settings.agent_run_ttl_seconds,
        )

    @staticmethod
    def _safe_error(exc: Exception) -> tuple[str, str]:
        if isinstance(exc, JobExecutionError):
            return exc.code, str(exc)[:300]
        if isinstance(exc, McpClientError):
            if exc.code in {"MCP_PERMISSION_DENIED", "MCP_UNAVAILABLE"}:
                return exc.code, str(exc)[:300]
            return "REPOSITORY_SCAN_FAILED", str(exc)[:300]
        if exc.__class__.__name__ == "RunStoreError":
            return "REDIS_UNAVAILABLE", "Redis is unavailable"
        if isinstance(exc, ValueError) and str(exc) == "UNIT_LIMIT_EXCEEDED":
            return "UNIT_LIMIT_EXCEEDED", "Analysis unit limit exceeded"
        return "INTERNAL_ERROR", "Agent job failed"

    @staticmethod
    def _now() -> str:
        return datetime.now(UTC).isoformat()


class JobExecutionError(RuntimeError):
    def __init__(self, code: str, message: str) -> None:
        super().__init__(message)
        self.code = code
