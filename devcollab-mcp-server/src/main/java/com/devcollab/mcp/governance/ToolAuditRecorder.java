package com.devcollab.mcp.governance;

import com.devcollab.mcp.error.McpToolErrorCode;

import java.util.UUID;

public interface ToolAuditRecorder {

    void record(ToolAuditEvent event);

    record ToolAuditEvent(
            String toolName,
            String toolCallId,
            UUID userId,
            UUID workspaceId,
            UUID repositoryId,
            long latencyMs,
            int inputSize,
            int outputSize,
            boolean truncated,
            String resultStatus,
            McpToolErrorCode errorCode
    ) {
    }
}
