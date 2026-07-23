package com.devcollab.mcp.error;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpToolErrorMapperTests {

    private final McpToolErrorMapper mapper = new McpToolErrorMapper();

    @Test
    void preservesKnownErrorSemanticsWithoutStackTrace() {
        McpSchema.CallToolResult result = mapper.map(
                new McpToolException(
                        McpToolErrorCode.FILE_NOT_FOUND,
                        "Repository file was not found",
                        Map.of("path", "src/App.java")
                )
        );

        Map<?, ?> error = (Map<?, ?>) ((Map<?, ?>) result.structuredContent()).get("error");
        assertThat(result.isError()).isTrue();
        assertThat(error.get("code")).isEqualTo("FILE_NOT_FOUND");
        assertThat(error.get("retryable")).isEqualTo(false);
        assertThat(error.containsKey("stackTrace")).isFalse();
    }

    @Test
    void marksCoreUnavailableAsRetryable() {
        McpSchema.CallToolResult result = mapper.map(
                new McpToolException(McpToolErrorCode.CORE_UNAVAILABLE, "Knowledge Core is unavailable")
        );
        Map<?, ?> error = (Map<?, ?>) ((Map<?, ?>) result.structuredContent()).get("error");

        assertThat(error.get("retryable")).isEqualTo(true);
    }
}
