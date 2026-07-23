package com.devcollab.mcp.error;

import java.util.Map;

public class McpToolException extends RuntimeException {

    private final McpToolErrorCode code;
    private final Map<String, Object> details;

    public McpToolException(McpToolErrorCode code, String message) {
        this(code, message, Map.of());
    }

    public McpToolException(McpToolErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = Map.copyOf(details);
    }

    public McpToolErrorCode code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }
}
