package com.devcollab.mcp.capability.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;

final class ToolResultFactory {

    private ToolResultFactory() {
    }

    static McpSchema.CallToolResult success(
            Map<String, Object> result,
            ObjectMapper objectMapper
    ) {
        try {
            return McpSchema.CallToolResult.builder()
                    .addTextContent(objectMapper.writeValueAsString(result))
                    .structuredContent(result)
                    .isError(false)
                    .build();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize tool result", exception);
        }
    }
}
