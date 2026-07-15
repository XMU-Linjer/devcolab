package com.devcollab.worker.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KafkaOutboxMessage(
        UUID eventId,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        String payload,
        Instant occurredAt
) {
}