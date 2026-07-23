package com.devcollab.mcp.error;

public enum McpToolErrorCode {
    INVALID_ARGUMENT(false),
    INVALID_LINE_RANGE(false),
    INVALID_REPOSITORY_PATH(false),
    UNSUPPORTED_FILE_TYPE(false),
    WORKSPACE_NOT_FOUND(false),
    REPOSITORY_NOT_FOUND(false),
    FILE_NOT_FOUND(false),
    DOCUMENT_NOT_FOUND(false),
    INVALID_DOCUMENT_QUERY(false),
    PERMISSION_DENIED(false),
    CONTEXT_LIMIT_EXCEEDED(false),
    CORE_UNAVAILABLE(true),
    INTERNAL_ERROR(false);

    private final boolean retryable;

    McpToolErrorCode(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
