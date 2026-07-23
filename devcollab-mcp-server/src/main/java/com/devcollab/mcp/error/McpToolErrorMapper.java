package com.devcollab.mcp.error;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class McpToolErrorMapper {

    private static final Logger log = LoggerFactory.getLogger(McpToolErrorMapper.class);

    public McpSchema.CallToolResult map(Throwable throwable) {
        McpToolException error;
        if (throwable instanceof McpToolException toolException) {
            error = toolException;
        } else {
            log.error("Unhandled MCP tool failure", throwable);
            error = new McpToolException(McpToolErrorCode.INTERNAL_ERROR, "The tool call could not be completed");
        }
        Map<String, Object> structured = Map.of(
                "error", Map.of(
                        "code", error.code().name(),
                        "message", error.getMessage(),
                        "retryable", error.code().retryable(),
                        "details", error.details()
                )
        );
        return McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(error.getMessage())))
                .structuredContent(structured)
                .isError(true)
                .build();
    }

    public McpServerFeatures.SyncToolSpecification protect(
            McpSchema.Tool tool,
            java.util.function.BiFunction<
                    io.modelcontextprotocol.server.McpSyncServerExchange,
                    McpSchema.CallToolRequest,
                    McpSchema.CallToolResult> handler
    ) {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        return handler.apply(exchange, request);
                    } catch (RuntimeException exception) {
                        return map(exception);
                    }
                })
                .build();
    }
}
