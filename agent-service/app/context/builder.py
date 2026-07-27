from typing import Any


def build_bundle(state: dict[str, Any]) -> dict[str, Any]:
    workspace_context = state["workspace_context"]
    repository_id = state["repository_id"]
    repository: dict[str, Any] = next(
        (
            item
            for item in workspace_context.get("repositories", [])
            if item.get("repositoryId") == repository_id
        ),
        {},
    )
    bound_ids = set(state.get("bound_document_ids", []))
    documents = [
        {
            "source": "BOUND" if structure.get("documentId") in bound_ids else "CANDIDATE",
            "documentId": structure["documentId"],
            "structure": structure,
        }
        for structure in state.get("document_structures", [])
    ]
    return {
        "runId": state["run_id"],
        "workspace": {
            "workspaceId": state["workspace_id"],
            "repositoryId": repository_id,
            "repositoryName": repository.get("name"),
            "defaultBranch": repository.get("defaultBranch"),
        },
        "task": {
            "selectedPaths": state["selected_paths"],
            "userInstruction": state.get("user_instruction"),
        },
        "codeFiles": state.get("code_files", []),
        "existingBindings": state.get("bindings", []),
        "documents": documents,
        "budget": {
            "toolCallsUsed": state.get("tool_call_count", 0),
            "codeCharsUsed": state.get("code_chars_used", 0),
            "truncatedFiles": state.get("truncated_files", []),
            "skippedDocumentIds": state.get("skipped_document_ids", []),
        },
    }
