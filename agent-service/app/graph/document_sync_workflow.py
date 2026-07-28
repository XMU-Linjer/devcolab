from collections.abc import Awaitable, Callable
from typing import Any, cast

from langgraph.graph import END, START, StateGraph

from app.clients.mcp_client import ReviewMcpClient
from app.config import Settings
from app.graph.state import AgentState
from app.graph.workflow import ContextWorkflow
from app.planning.context_serializer import build_model_context
from app.planning.validator import AgentPlanValidator, PlanValidationError
from app.providers.base import ModelProvider, ModelProviderError
from app.schemas.plans import AgentPlan, Decision
from app.tracing.trace_logger import traced

StatusCallback = Callable[[str, str, dict[str, Any]], Awaitable[None]]


class ReviewSubmissionError(RuntimeError):
    code = "REVIEW_SUBMISSION_FAILED"


class DocumentSyncWorkflow:
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
        self._validator = AgentPlanValidator(
            settings.agent_review_max_operations,
            settings.agent_review_max_evidence,
        )
        context = ContextWorkflow(client, settings)
        graph = StateGraph(AgentState)
        graph.add_node("load_workspace_context", context.load_workspace_context)
        graph.add_node("read_selected_code", context.read_selected_code)
        graph.add_node("list_existing_bindings", context.list_existing_bindings)
        graph.add_node("resolve_documents", context.resolve_documents)
        graph.add_node("read_document_structures", context.read_document_structures)
        graph.add_node("build_context_bundle", context.build_context_bundle)
        graph.add_node("plan_changes", self.plan_changes)
        graph.add_node("validate_plan", self.validate_plan)
        graph.add_node("repair_plan", self.repair_plan)
        graph.add_node("validate_repaired_plan", self.validate_repaired_plan)
        graph.add_node("finish_no_change", self.finish_no_change)
        graph.add_node("submit_review", self.submit_review)
        graph.add_node("fail_run", self.fail_run)
        graph.add_edge(START, "load_workspace_context")
        graph.add_edge("load_workspace_context", "read_selected_code")
        graph.add_edge("read_selected_code", "list_existing_bindings")
        graph.add_edge("list_existing_bindings", "resolve_documents")
        graph.add_edge("resolve_documents", "read_document_structures")
        graph.add_edge("read_document_structures", "build_context_bundle")
        graph.add_edge("build_context_bundle", "plan_changes")
        graph.add_edge("plan_changes", "validate_plan")
        graph.add_conditional_edges(
            "validate_plan",
            self._route,
            {
                "NO_CHANGE": "finish_no_change",
                "SUBMIT_REVIEW": "submit_review",
                "REPAIR": "repair_plan",
            },
        )
        graph.add_edge("repair_plan", "validate_repaired_plan")
        graph.add_conditional_edges(
            "validate_repaired_plan",
            self._route,
            {
                "NO_CHANGE": "finish_no_change",
                "SUBMIT_REVIEW": "submit_review",
                "INVALID": "fail_run",
            },
        )
        graph.add_edge("finish_no_change", END)
        graph.add_edge("submit_review", END)
        graph.add_edge("fail_run", END)
        self.graph = graph.compile()

    async def execute_context_bundle(self, state: AgentState) -> dict[str, Any]:
        mutable = cast(dict[str, Any], state)
        mutable.update(await self.plan_changes(state))
        mutable.update(await self.validate_plan(state))
        route = self._route(state)
        if route == "NO_CHANGE":
            mutable.update(await self.finish_no_change(state))
            return mutable
        if route == "SUBMIT_REVIEW":
            mutable.update(await self.submit_review(state))
            return mutable
        mutable.update(await self.repair_plan(state))
        mutable.update(await self.validate_repaired_plan(state))
        route = self._route(state)
        if route == "NO_CHANGE":
            mutable.update(await self.finish_no_change(state))
            return mutable
        if route == "SUBMIT_REVIEW":
            mutable.update(await self.submit_review(state))
            return mutable
        await self.fail_run(state)
        raise AssertionError("unreachable")

    async def plan_changes(self, state: AgentState) -> dict[str, Any]:
        await self._on_status("PLANNING", "plan_changes", {})
        model_context = build_model_context(state["context_bundle"])
        input_characters = len(str(model_context))
        if input_characters > self._settings.agent_model_max_input_characters:
            raise ValueError("Model context exceeds configured input limit")
        try:
            plan = await traced(
                cast(dict[str, Any], state),
                "plan_changes",
                None,
                lambda: self._provider.plan_document_sync(model_context),
                input_characters,
            )
            return {
                "model_context": model_context,
                "plan": plan,
                "validation_attempt": 0,
                "trace_events": state["trace_events"],
            }
        except ModelProviderError as exc:
            if exc.code != "MODEL_INVALID_RESPONSE":
                raise
            return {
                "model_context": model_context,
                "previous_plan": exc.raw_plan or {},
                "validation_errors": [
                    {
                        "path": "$",
                        "code": "MODEL_INVALID_RESPONSE",
                        "message": "Return a complete AgentPlan matching the schema",
                    }
                ],
                "validation_attempt": 0,
                "trace_events": state["trace_events"],
            }

    async def validate_plan(self, state: AgentState) -> dict[str, Any]:
        await self._on_status("VALIDATING", "validate_plan", {})
        plan = state.get("plan")
        if not isinstance(plan, AgentPlan):
            return {"plan_outcome": "REPAIR"}
        try:
            valid = self._validator.validate(plan, state["model_context"])
        except PlanValidationError as exc:
            return {
                "previous_plan": plan.model_dump(mode="json", exclude_none=True),
                "validation_errors": exc.safe_details(),
                "plan_outcome": "REPAIR",
            }
        return {
            "plan": valid,
            "decision": valid.decision.value,
            "summary": valid.summary,
            "plan_outcome": valid.decision.value,
        }

    async def repair_plan(self, state: AgentState) -> dict[str, Any]:
        await self._on_status("REPAIRING_PLAN", "repair_plan", {})
        try:
            plan = await traced(
                cast(dict[str, Any], state),
                "repair_plan",
                None,
                lambda: self._provider.plan_document_sync(
                    state["model_context"],
                    previous_plan=state.get("previous_plan", {}),
                    validation_errors=state.get("validation_errors", []),
                ),
                len(str(state.get("validation_errors", []))),
            )
            return {
                "plan": plan,
                "validation_attempt": 1,
                "trace_events": state["trace_events"],
            }
        except ModelProviderError as exc:
            if exc.code != "MODEL_INVALID_RESPONSE":
                raise
            return {
                "plan": None,
                "validation_attempt": 1,
                "validation_errors": [
                    {
                        "path": "$",
                        "code": "MODEL_INVALID_RESPONSE",
                        "message": "Repaired output still does not match AgentPlan",
                    }
                ],
                "trace_events": state["trace_events"],
            }

    async def validate_repaired_plan(self, state: AgentState) -> dict[str, Any]:
        await self._on_status("VALIDATING", "validate_repaired_plan", {})
        plan = state.get("plan")
        if not isinstance(plan, AgentPlan):
            return {"plan_outcome": "INVALID"}
        try:
            valid = self._validator.validate(plan, state["model_context"])
        except PlanValidationError as exc:
            return {
                "validation_errors": exc.safe_details(),
                "plan_outcome": "INVALID",
            }
        return {
            "plan": valid,
            "decision": valid.decision.value,
            "summary": valid.summary,
            "plan_outcome": valid.decision.value,
        }

    async def finish_no_change(self, state: AgentState) -> dict[str, Any]:
        plan = cast(AgentPlan, state["plan"])
        await self._on_status(
            "NO_CHANGE",
            "finish_no_change",
            {"decision": Decision.NO_CHANGE.value, "summary": plan.summary},
        )
        return {
            "decision": Decision.NO_CHANGE.value,
            "summary": plan.summary,
        }

    async def submit_review(self, state: AgentState) -> dict[str, Any]:
        await self._on_status(
            "SUBMITTING_REVIEW",
            "submit_review",
            {
                "decision": Decision.SUBMIT_REVIEW.value,
                "summary": state.get("summary"),
            },
        )
        plan = cast(AgentPlan, state["plan"])
        try:
            result = await traced(
                cast(dict[str, Any], state),
                "submit_review",
                "devcollab.review.submit_document_change",
                lambda: self._client.submit_document_change(
                    plan,
                    workspace_id=state["workspace_id"],
                    run_id=state["run_id"],
                    authorization=state["authorization"],
                ),
                len(str(plan.model_dump(exclude_none=True))),
            )
        except Exception as exc:
            if hasattr(exc, "code"):
                raise
            raise ReviewSubmissionError("Could not submit review request") from exc
        change_request_id = result.get("changeRequestId")
        if result.get("status") != "PENDING" or not change_request_id:
            raise ReviewSubmissionError("MCP returned an invalid review result")
        await self._on_status(
            "REVIEW_SUBMITTED",
            "submit_review",
            {
                "decision": Decision.SUBMIT_REVIEW.value,
                "summary": plan.summary,
                "changeRequestId": str(change_request_id),
            },
        )
        return {
            "decision": Decision.SUBMIT_REVIEW.value,
            "summary": plan.summary,
            "change_request_id": str(change_request_id),
        }

    async def fail_run(self, state: AgentState) -> dict[str, Any]:
        raise PlanValidationError(
            [self._issue_from_dict(item) for item in state.get("validation_errors", [])]
        )

    @staticmethod
    def _issue_from_dict(item: dict[str, str]) -> Any:
        from app.schemas.plans import PlanValidationIssue

        return PlanValidationIssue.model_validate(item)

    @staticmethod
    def _route(state: AgentState) -> str:
        return state["plan_outcome"]
