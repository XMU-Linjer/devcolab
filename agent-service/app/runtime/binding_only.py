from __future__ import annotations

from typing import Any, cast

from app.clients.mcp_client import ReviewMcpClient
from app.config import Settings
from app.graph.document_sync_workflow import DocumentSyncWorkflow, StatusCallback
from app.graph.state import AgentState
from app.planning.context_serializer import build_model_context
from app.providers.base import ModelProvider
from app.runtime.project_unit_context import ProjectUnitContextBuilder
from app.schemas.plans import AgentPlan, Decision


class BindingOnlyWorkflow:
    """Focused verification/repair entry that never invokes the project planner."""

    def __init__(
        self,
        client: ReviewMcpClient,
        provider: ModelProvider,
        settings: Settings,
        on_status: StatusCallback,
    ) -> None:
        self._client = client
        self._provider = provider
        self._settings = settings
        self._on_status = on_status

    async def run(
        self,
        *,
        run_id: str,
        workspace_id: str,
        repository_id: str,
        revision: str,
        file_paths: list[str],
        document_id: str,
    ) -> dict[str, Any]:
        state = await ProjectUnitContextBuilder(
            self._client, self._settings
        ).build(
            run_id=run_id,
            workspace_id=workspace_id,
            repository_id=repository_id,
            revision=revision,
            selected_paths=file_paths,
            preferred_document_ids=[document_id],
            user_instruction="仅规划真实代码候选与现有文档 Block 的关联。",
        )
        state["model_context"] = build_model_context(state["context_bundle"])
        state["plan"] = AgentPlan(
            decision=Decision.NO_CHANGE,
            summary="代码与文档块级关联审查",
            rationale="基于固定代码版本和现有文档 Block 候选生成关联提案。",
        )
        workflow = DocumentSyncWorkflow(
            self._client, self._provider, self._settings, self._on_status
        )
        state = cast(
            AgentState,
            {**state, **await workflow.plan_bindings(state)},
        )
        if state["plan_outcome"] == Decision.NO_CHANGE.value:
            return {
                "decision": Decision.NO_CHANGE.value,
                "summary": state["plan"].summary,
                "candidateSelectionCount": 0,
            }
        state = cast(
            AgentState,
            {**state, **await workflow.submit_review(state)},
        )
        return {
            "decision": state["decision"],
            "summary": state["summary"],
            "changeRequestId": state["change_request_id"],
            "candidateSelectionCount": len(state["plan"].bindingProposals),
        }
