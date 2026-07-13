package com.devcollab.knowledgecore.common.outbox.domain;

import java.time.Instant;
import java.util.UUID;

public record OutboxEvent(
        UUID id,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        String payload,
        OutboxEventStatus status,
        Instant occurredAt,
        Instant publishedAt,
        int retryCount,
        String lastError
) {
    public static OutboxEvent pending(
            String aggregateType,
            UUID aggregateId,
            String eventType,
            String payload,
            Instant occurredAt
    ) {
        return new OutboxEvent(
                UUID.randomUUID(),
                aggregateType,
                aggregateId,
                eventType,
                payload,
                OutboxEventStatus.PENDING,
                occurredAt,
                null,
                0,
                null
        );
    }
}
