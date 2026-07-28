from __future__ import annotations

from collections import Counter
from collections.abc import Awaitable, Callable
from typing import Any
from uuid import NAMESPACE_URL, UUID, uuid5

from app.clients.mcp_client import McpClientError, ReadOnlyMcpClient
from app.config import Settings
from app.runtime.file_classification import ClassifiedFile, classify_file
from app.runtime.job_executor import JobExecutionError
from app.runtime.semantic_planner import (
    PlannedSemanticUnit,
    ProjectFile,
    build_semantic_units,
    overlapping_file_count,
)

PhaseCallback = Callable[[str], Awaitable[None]]


class ProjectDiscoveryService:
    def __init__(
        self,
        client: ReadOnlyMcpClient,
        settings: Settings,
        on_phase: PhaseCallback,
    ) -> None:
        self._client = client
        self._settings = settings
        self._on_phase = on_phase

    async def execute(
        self,
        *,
        job_id: UUID,
        workspace_id: UUID,
        repository_id: UUID,
        revision: str,
    ) -> tuple[list[dict[str, Any]], list[PlannedSemanticUnit], dict[str, Any]]:
        await self._on_phase("DISCOVERING_FILES")
        discovered = await self._discover(
            str(workspace_id), str(repository_id), revision
        )
        await self._on_phase("CLASSIFYING_FILES")
        classified = [
            classify_file(
                item,
                max_size_bytes=self._settings.agent_max_single_file_bytes,
            )
            for item in discovered
        ]
        eligible = [item for item in classified if item.eligible]
        skipped = [item for item in classified if not item.eligible]

        await self._on_phase("LOADING_CODE_METADATA")
        metadata_by_path, metadata_failures = await self._load_metadata(
            str(workspace_id),
            str(repository_id),
            revision,
            [item.file_path for item in eligible],
        )
        project_files = [
            self._project_file(job_id, item, metadata_by_path.get(item.file_path, {}))
            for item in eligible
        ]

        await self._on_phase("LOADING_BINDINGS")
        bindings = await self._load_bindings(
            str(workspace_id),
            str(repository_id),
            [item.file_path for item in eligible],
        )
        await self._on_phase("BUILDING_SEMANTIC_GRAPH")
        await self._on_phase("BUILDING_ANALYSIS_UNITS")
        units = build_semantic_units(
            project_files,
            bindings,
            job_id=job_id,
            revision=revision,
            max_primary_files=self._settings.agent_max_primary_files_per_unit,
            max_supporting_files=self._settings.agent_max_supporting_files_per_unit,
            max_total_files=self._settings.agent_max_total_files_per_unit,
            max_units=self._settings.agent_max_analysis_units,
        )
        all_rows = [
            self._file_row(
                job_id, repository_id, revision, item,
                metadata_by_path.get(item.file_path, {}),
            )
            for item in classified
        ]
        bound_file_count = sum(bool(bindings.get(item.file_path)) for item in eligible)
        stats = {
            "discovered_file_count": len(discovered),
            "supported_code_count": len(eligible),
            "skipped_file_count": len(skipped),
            "skipped_reason_counts": dict(
                sorted(Counter(item.classification for item in skipped).items())
            ),
            "metadata_parsed_count": len(eligible) - metadata_failures,
            "metadata_failed_count": metadata_failures,
            "bound_file_count": bound_file_count,
            "unbound_file_count": len(eligible) - bound_file_count,
            "analysis_unit_count": len(units),
            "overlapping_file_count": overlapping_file_count(units),
        }
        return all_rows, units, stats

    async def _discover(
        self,
        workspace_id: str,
        repository_id: str,
        revision: str,
    ) -> list[dict[str, Any]]:
        cursor: str | None = None
        seen_cursors: set[str] = set()
        unique: dict[str, dict[str, Any]] = {}
        for _ in range(self._settings.agent_max_discovery_pages):
            arguments: dict[str, Any] = {
                "workspaceId": workspace_id,
                "repositoryId": repository_id,
                "revision": revision,
                "pathPrefix": "",
                "recursive": True,
                "limit": self._settings.agent_project_list_page_size,
            }
            if cursor:
                arguments["cursor"] = cursor
            response = await self._client.call_tool(
                "devcollab.repository.list_files", arguments, "delegated"
            )
            actual_revision_value = response.get("revision")
            if not isinstance(actual_revision_value, str) or not actual_revision_value:
                raise JobExecutionError(
                    "REPOSITORY_SCAN_FAILED",
                    "Repository file page did not declare its revision",
                )
            actual_revision = actual_revision_value
            if actual_revision.lower() != revision.lower():
                raise JobExecutionError(
                    "REVISION_CHANGED",
                    "Repository revision changed after the Agent job was created",
                )
            for item in response.get("files", []):
                path = str(item.get("filePath", "")).replace("\\", "/")
                if path:
                    unique[path] = dict(item)
            if len(unique) > self._settings.agent_project_max_files:
                raise JobExecutionError(
                    "DISCOVERY_LIMIT_EXCEEDED",
                    "Repository exceeds the configured file limit",
                )
            if not response.get("hasMore"):
                return [unique[path] for path in sorted(unique)]
            next_cursor = response.get("nextCursor")
            if (
                not isinstance(next_cursor, str)
                or not next_cursor
                or next_cursor in seen_cursors
            ):
                raise JobExecutionError(
                    "REPOSITORY_SCAN_FAILED",
                    "Repository pagination cursor did not advance",
                )
            seen_cursors.add(next_cursor)
            cursor = next_cursor
        raise JobExecutionError(
            "DISCOVERY_LIMIT_EXCEEDED",
            "Repository discovery page limit exceeded",
        )

    async def _load_metadata(
        self,
        workspace_id: str,
        repository_id: str,
        revision: str,
        paths: list[str],
    ) -> tuple[dict[str, dict[str, Any]], int]:
        result: dict[str, dict[str, Any]] = {}
        failures = 0
        size = self._settings.agent_metadata_batch_size
        for index in range(0, len(paths), size):
            chunk = paths[index:index + size]
            response = await self._client.call_tool(
                "devcollab.repository.inspect_code_metadata",
                {
                    "workspaceId": workspace_id,
                    "repositoryId": repository_id,
                    "revision": revision,
                    "filePaths": chunk,
                },
                "delegated",
            )
            actual_revision = response.get("revision")
            if (
                not isinstance(actual_revision, str)
                or actual_revision.lower() != revision.lower()
            ):
                raise JobExecutionError(
                    "REVISION_CHANGED",
                    "Code metadata revision does not match the Agent job revision",
                )
            returned_paths: set[str] = set()
            for item in response.get("files", []):
                path = str(item.get("filePath", ""))
                if path not in chunk or path in returned_paths:
                    continue
                returned_paths.add(path)
                result[path] = dict(item)
                if item.get("parseStatus") != "PARSED":
                    failures += 1
            for missing_path in set(chunk) - returned_paths:
                result[missing_path] = {
                    "filePath": missing_path,
                    "parseStatus": "FAILED",
                    "errorCode": "METADATA_MISSING",
                }
                failures += 1
        return result, failures

    async def _load_bindings(
        self,
        workspace_id: str,
        repository_id: str,
        paths: list[str],
    ) -> dict[str, list[dict[str, Any]]]:
        result: dict[str, list[dict[str, Any]]] = {path: [] for path in paths}
        try:
            size = self._settings.agent_binding_batch_size
            for index in range(0, len(paths), size):
                chunk = paths[index:index + size]
                response = await self._client.call_tool(
                    "devcollab.binding.list_batch",
                    {
                        "workspaceId": workspace_id,
                        "repositoryId": repository_id,
                        "filePaths": chunk,
                    },
                    "delegated",
                )
                for group in response.get("files", []):
                    result[str(group["filePath"])] = list(group.get("bindings", []))
        except McpClientError as exc:
            raise JobExecutionError("BINDING_BATCH_FAILED", str(exc)[:300]) from exc
        return result

    @staticmethod
    def _project_file(
        job_id: UUID,
        item: ClassifiedFile,
        metadata: dict[str, Any],
    ) -> ProjectFile:
        return ProjectFile(
            id=uuid5(NAMESPACE_URL, f"{job_id}:{item.file_path}"),
            file_path=item.file_path,
            language=item.language or "Unknown",
            size_bytes=item.size_bytes,
            package_name=_optional_text(metadata.get("packageName")),
            module_key=_optional_text(metadata.get("moduleKey")),
            layer_hint=_optional_text(metadata.get("layerHint")),
            role_hints=tuple(_string_list(metadata.get("roleHints"))),
            import_keys=tuple(_string_list(metadata.get("imports"))),
            exported_symbols=tuple(_string_list(metadata.get("exportedSymbols"))),
            top_level_symbols=tuple(_string_list(metadata.get("topLevelSymbols"))),
        )

    @staticmethod
    def _file_row(
        job_id: UUID,
        repository_id: UUID,
        revision: str,
        item: ClassifiedFile,
        metadata: dict[str, Any],
    ) -> dict[str, Any]:
        return {
            "id": uuid5(NAMESPACE_URL, f"{job_id}:{item.file_path}"),
            "job_id": job_id,
            "repository_id": repository_id,
            "revision": revision,
            "file_path": item.file_path,
            "file_name": item.file_name,
            "extension": item.extension,
            "language": item.language,
            "size_bytes": item.size_bytes,
            "classification": item.classification,
            "package_name": _optional_text(metadata.get("packageName")),
            "module_key": _optional_text(metadata.get("moduleKey")),
            "layer_hint": _optional_text(metadata.get("layerHint")),
            "role_hints": _string_list(metadata.get("roleHints")),
            "import_keys": _string_list(metadata.get("imports")),
            "exported_symbols": _string_list(metadata.get("exportedSymbols")),
            "top_level_symbols": _string_list(metadata.get("topLevelSymbols")),
            "is_generated": item.is_generated,
            "metadata_error": _optional_text(metadata.get("errorCode")),
        }


def _string_list(value: object) -> list[str]:
    if not isinstance(value, list):
        return []
    return [str(item) for item in value if isinstance(item, str)]


def _optional_text(value: object) -> str | None:
    return str(value) if isinstance(value, str) and value else None
