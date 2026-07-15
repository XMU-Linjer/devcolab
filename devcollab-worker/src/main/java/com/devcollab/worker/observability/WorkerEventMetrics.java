package com.devcollab.worker.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

/**
 * Centralized metric names for the Worker event pipeline.
 *
 * <p>The goal is not to replace logs, but to make important chain states
 * countable: malformed messages, duplicated events, successful projections,
 * failed projections, retries and DLQ routing.
 */
@Component
public class WorkerEventMetrics {

    private static final String UNKNOWN = "unknown";

    private final MeterRegistry meterRegistry;

    public WorkerEventMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void messageMalformed(String consumerName) {
        counter(
                "devcollab.worker.event.malformed",
                "consumer", consumerName
        ).increment();
    }

    public void payloadMalformed(String consumerName, String eventType) {
        counter(
                "devcollab.worker.event.payload_malformed",
                "consumer", consumerName,
                "event_type", sanitize(eventType)
        ).increment();
    }

    public void duplicateSkipped(String consumerName, String eventType) {
        counter(
                "devcollab.worker.event.duplicate_skipped",
                "consumer", consumerName,
                "event_type", sanitize(eventType)
        ).increment();
    }

    public void projectionSucceeded(String consumerName, String eventType) {
        counter(
                "devcollab.worker.event.projected",
                "consumer", consumerName,
                "event_type", sanitize(eventType)
        ).increment();
    }

    public void projectionFailed(String consumerName, String eventType) {
        counter(
                "devcollab.worker.event.projection_failed",
                "consumer", consumerName,
                "event_type", sanitize(eventType)
        ).increment();
    }

    public void retrying(ConsumerRecord<?, ?> record) {
        counter(
                "devcollab.worker.kafka.retry",
                "topic", sanitize(record.topic())
        ).increment();
    }

    public void sentToDlq(ConsumerRecord<?, ?> record, String dlqTopic) {
        counter(
                "devcollab.worker.kafka.dlq",
                "source_topic", sanitize(record.topic()),
                "dlq_topic", sanitize(dlqTopic)
        ).increment();
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name)
                .tags(tags)
                .register(meterRegistry);
    }

    private String sanitize(String value) {
        return value == null || value.isBlank() ? UNKNOWN : value;
    }
}
