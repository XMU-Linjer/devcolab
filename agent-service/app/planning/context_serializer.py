from typing import Any


def build_model_context(bundle: dict[str, Any]) -> dict[str, Any]:
    """Create the only representation that may cross the model boundary."""
    code_files = [
        {
            "filePath": item.get("filePath"),
            "language": item.get("language"),
            "content": item.get("content", ""),
            "truncated": bool(item.get("truncated", False)),
        }
        for item in bundle.get("codeFiles", [])
    ]
    bindings = [
        {
            key: item.get(key)
            for key in (
                "bindingId",
                "filePath",
                "pathPattern",
                "documentId",
                "documentTitle",
                "blockId",
                "bindingType",
            )
            if item.get(key) is not None
        }
        for item in bundle.get("existingBindings", [])
    ]
    documents = sorted(
        bundle.get("documents", []),
        key=lambda item: 0 if item.get("source") == "BOUND" else 1,
    )
    safe_documents = []
    for item in documents:
        structure = item.get("structure", {})
        safe_documents.append(
            {
                "source": item.get("source"),
                "documentId": item.get("documentId"),
                "title": structure.get("title"),
                "documentType": structure.get("documentType"),
                "reviewStatus": structure.get("reviewStatus"),
                "version": structure.get("version"),
                "blocks": [
                    {
                        key: block.get(key)
                        for key in (
                            "blockId",
                            "type",
                            "sortOrder",
                            "version",
                            "plainText",
                            "content",
                            "contentTruncated",
                        )
                        if block.get(key) is not None
                    }
                    for block in structure.get("blocks", [])
                ],
            }
        )
    workspace = bundle.get("workspace", {})
    task = bundle.get("task", {})
    return {
        "workspace": {
            "workspaceId": workspace.get("workspaceId"),
            "repositoryId": workspace.get("repositoryId"),
            "repositoryName": workspace.get("repositoryName"),
            "defaultBranch": workspace.get("defaultBranch"),
        },
        "task": {
            "selectedPaths": task.get("selectedPaths", []),
            "userInstruction": task.get("userInstruction"),
        },
        "codeFiles": code_files,
        "existingBindings": bindings,
        "documents": safe_documents,
    }
