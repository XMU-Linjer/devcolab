from typing import Any, TypedDict


class AgentState(TypedDict, total=False):
    run_id: str
    workspace_id: str
    repository_id: str
    selected_paths: list[str]
    user_instruction: str | None
    authorization: str
    workspace_context: dict[str, Any]
    code_files: list[dict[str, Any]]
    bindings: list[dict[str, Any]]
    bound_document_ids: list[str]
    candidate_documents: list[dict[str, Any]]
    document_structures: list[dict[str, Any]]
    tool_call_count: int
    code_chars_used: int
    truncated_files: list[str]
    skipped_document_ids: list[str]
    trace_events: list[dict[str, Any]]
    errors: list[dict[str, Any]]
    context_bundle: dict[str, Any]
