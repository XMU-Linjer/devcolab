package com.devcollab.mcp.governance;

import com.devcollab.mcp.error.McpToolErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingToolAuditRecorderTests {

    @Test
    void recordsLowCardinalityMetricsWithoutSensitiveLabels() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LoggingToolAuditRecorder recorder = new LoggingToolAuditRecorder(registry);
        ToolAuditRecorder.ToolAuditEvent event = new ToolAuditRecorder.ToolAuditEvent(
                "devcollab.code.read",
                UUID.randomUUID().toString(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                12,
                100,
                200,
                true,
                "ERROR",
                McpToolErrorCode.CONTEXT_LIMIT_EXCEEDED
        );

        recorder.record(event);

        assertThat(registry.get("devcollab.mcp.tool.calls").counter().count()).isEqualTo(1);
        assertThat(registry.get("devcollab.mcp.tool.duration").timer().count()).isEqualTo(1);
        assertThat(registry.get("devcollab.mcp.tool.calls").counter().getId().getTags())
                .extracting(tag -> tag.getKey())
                .containsExactlyInAnyOrder("tool", "status", "error")
                .doesNotContain("userId", "workspaceId", "repositoryId", "path");
    }
}
