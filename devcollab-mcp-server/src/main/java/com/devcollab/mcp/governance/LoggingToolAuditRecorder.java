package com.devcollab.mcp.governance;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class LoggingToolAuditRecorder implements ToolAuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(LoggingToolAuditRecorder.class);
    private final MeterRegistry meterRegistry;

    public LoggingToolAuditRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void record(ToolAuditEvent event) {
        String error = event.errorCode() == null ? "NONE" : event.errorCode().name();
        meterRegistry.counter(
                "devcollab.mcp.tool.calls",
                "tool", event.toolName(),
                "status", event.resultStatus(),
                "error", error
        ).increment();
        meterRegistry.timer(
                "devcollab.mcp.tool.duration",
                "tool", event.toolName(),
                "status", event.resultStatus()
        ).record(Duration.ofMillis(event.latencyMs()));
        log.info(
                "MCP tool audit tool={} callId={} userId={} workspaceId={} repositoryId={} latencyMs={} inputSize={} outputSize={} truncated={} status={} error={}",
                event.toolName(),
                event.toolCallId(),
                event.userId(),
                event.workspaceId(),
                event.repositoryId(),
                event.latencyMs(),
                event.inputSize(),
                event.outputSize(),
                event.truncated(),
                event.resultStatus(),
                error
        );
    }
}
