package com.devcollab.mcp.governance;

import com.devcollab.mcp.error.McpToolErrorCode;
import com.devcollab.mcp.error.McpToolException;
import com.devcollab.mcp.security.McpUserIdentity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public class AuditedToolExecutor {

    private final ToolAuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public AuditedToolExecutor(ToolAuditRecorder auditRecorder, ObjectMapper objectMapper) {
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> execute(
            String toolName,
            McpUserIdentity identity,
            UUID workspaceId,
            UUID repositoryId,
            Map<String, Object> arguments,
            Supplier<Map<String, Object>> invocation
    ) {
        long startedAt = System.nanoTime();
        String callId = UUID.randomUUID().toString();
        try {
            Map<String, Object> result = invocation.get();
            auditRecorder.record(event(
                    toolName, callId, identity, workspaceId, repositoryId, startedAt,
                    jsonSize(arguments), jsonSize(result),
                    Boolean.TRUE.equals(result.get("truncated")), "SUCCESS", null
            ));
            return result;
        } catch (RuntimeException exception) {
            McpToolErrorCode errorCode = exception instanceof McpToolException toolException
                    ? toolException.code()
                    : McpToolErrorCode.INTERNAL_ERROR;
            auditRecorder.record(event(
                    toolName, callId, identity, workspaceId, repositoryId, startedAt,
                    jsonSize(arguments), 0, false, "ERROR", errorCode
            ));
            throw exception;
        }
    }

    private ToolAuditRecorder.ToolAuditEvent event(
            String toolName,
            String callId,
            McpUserIdentity identity,
            UUID workspaceId,
            UUID repositoryId,
            long startedAt,
            int inputSize,
            int outputSize,
            boolean truncated,
            String status,
            McpToolErrorCode errorCode
    ) {
        return new ToolAuditRecorder.ToolAuditEvent(
                toolName,
                callId,
                identity.userId(),
                workspaceId,
                repositoryId,
                (System.nanoTime() - startedAt) / 1_000_000,
                inputSize,
                outputSize,
                truncated,
                status,
                errorCode
        );
    }

    private int jsonSize(Object value) {
        try {
            return objectMapper.writeValueAsString(value).length();
        } catch (JsonProcessingException exception) {
            return 0;
        }
    }
}
