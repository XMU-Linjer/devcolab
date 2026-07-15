package com.devcollab.knowledgecore.common.outbox.application;

import com.devcollab.knowledgecore.common.outbox.domain.OutboxEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka message body published by the Outbox Relay.
 *
 * <p>Key: aggregateId (ensures ordered delivery within the same aggregate).
 * <p>Value: JSON with eventId, aggregateType, aggregateId, eventType, payload, occurredAt.
 */
public record OutboxKafkaMessage(
        UUID eventId,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        String payload,
        Instant occurredAt
) {
    public static OutboxKafkaMessage from(OutboxEvent event) {
        return new OutboxKafkaMessage(
                event.id(),
                event.aggregateType(),
                event.aggregateId(),
                event.eventType(),
                event.payload(),
                event.occurredAt()
        );
    }

    /**
     * Kafka record key – aggregateId ensures ordering within the same aggregate.
     */
    public String kafkaKey() {
        return aggregateId.toString();
    }
}