from __future__ import annotations

from typing import Any

from app.clients.mcp_client import ReadOnlyMcpClient
from app.config import Settings
from app.context.builder import build_bundle
from app.graph.state import AgentState
from app.runtime.job_executor import JobExecutionError


class ProjectUnitContextBuilder:
    def __init__(self, client: ReadOnlyMcpClient, settings: Settings) -> None:
        self._client = client
        self._settings = settings

    async def build(
        self,
        *,
        run_id: str,
        workspace_id: str,
        repository_id: str,
        revision: str,
        selected_paths: list[str],
        preferred_document_ids: list[str],
        user_instruction: str | None,
    ) -> AgentState:
        calls = 0

        async def call(tool: str, arguments: dict[str, Any]) -> dict[str, Any]:
            nonlocal calls
            calls += 1
            if calls > self._settings.agent_project_max_tool_calls:
                raise JobExecutionError(
                    "CONTEXT_LIMIT_EXCEEDED",
                    "Project unit exceeded the configured MCP tool-call budget",
                )
            return await self._client.call_tool(tool, arguments, "delegated")

        workspace = await call(
            "devcollab.workspace.get_context",
            {"workspaceId": workspace_id},
        )
        if not any(
            str(item.get("repositoryId")) == repository_id
            for item in workspace.get("repositories", [])
        ):
            raise JobExecutionError(
                "REPOSITORY_NOT_FOUND",
                "Repository is not registered in the workspace",
            )

        remaining = self._settings.agent_max_code_chars
        code_files: list[dict[str, Any]] = []
        truncated_files: list[str] = []
        for path in selected_paths:
            source = await call(
                "devcollab.code.read",
                {
                    "workspaceId": workspace_id,
                    "repositoryId": repository_id,
                    "path": path,
                },
            )
            if str(source.get("commitHash", "")).lower() != revision.lower():
                raise JobExecutionError(
                    "REVISION_CHANGED",
                    "Repository revision changed while building Unit context",
                )
            content = str(source.get("content", ""))
            truncated = bool(source.get("truncated", False))
            if remaining <= 0:
                content = ""
                truncated = True
            elif len(content) > remaining:
                content = content[:remaining]
                truncated = True
            remaining = max(0, remaining - len(content))
            if truncated:
                truncated_files.append(path)
            code_files.append(
                {
                    "filePath": source.get("path", path),
                    "language": source.get("language"),
                    "content": content,
                    "truncated": truncated,
                }
            )

        batch = await call(
            "devcollab.binding.list_batch",
            {
                "workspaceId": workspace_id,
                "repositoryId": repository_id,
                "filePaths": selected_paths,
            },
        )
        bindings: list[dict[str, Any]] = []
        document_ids = list(dict.fromkeys(preferred_document_ids))
        for group in batch.get("files", []):
            path = str(group.get("filePath", ""))
            for binding in group.get("bindings", []):
                bindings.append({"filePath": path, **dict(binding)})
                document_id = str(binding.get("documentId") or "")
                if document_id and document_id not in document_ids:
                    document_ids.append(document_id)

        selected_document_ids = document_ids[
            : self._settings.agent_max_document_structures
        ]
        structures = [
            await call(
                "devcollab.document.get_structure",
                {
                    "workspaceId": workspace_id,
                    "documentId": document_id,
                    "includeBlockContent": True,
                },
            )
            for document_id in selected_document_ids
        ]
        state: AgentState = {
            "run_id": run_id,
            "workspace_id": workspace_id,
            "repository_id": repository_id,
            "selected_paths": selected_paths,
            "preferred_document_ids": preferred_document_ids,
            "user_instruction": user_instruction,
            "authorization": "delegated",
            "workspace_context": workspace,
            "code_files": code_files,
            "bindings": bindings,
            "bound_document_ids": document_ids,
            "candidate_documents": [],
            "document_structures": structures,
            "tool_call_count": calls,
            "code_chars_used": self._settings.agent_max_code_chars - remaining,
            "truncated_files": truncated_files,
            "skipped_document_ids": document_ids[len(selected_document_ids) :],
            "trace_events": [],
            "errors": [],
        }
        state["context_bundle"] = build_bundle(dict(state))
        return state
