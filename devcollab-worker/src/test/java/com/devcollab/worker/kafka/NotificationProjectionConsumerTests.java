package com.devcollab.worker.kafka;

import com.devcollab.worker.consumerinbox.ConsumerInboxRepository;
import com.devcollab.worker.notification.NotificationProjectionService;
import com.devcollab.worker.observability.WorkerEventMetrics;
import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NotificationProjectionConsumerTests {

    private final ConsumerInboxRepository inboxRepository =
            mock(ConsumerInboxRepository.class);
    private final NotificationProjectionService projectionService =
            mock(NotificationProjectionService.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final WorkerEventMetrics metrics =
            new WorkerEventMetrics(meterRegistry);
    private final NotificationProjectionConsumer consumer =
            new NotificationProjectionConsumer(
                    inboxRepository,
                    projectionService,
                    metrics
            );

    @Test
    void skipsAlreadyConsumedEvent() {
        UUID eventId = UUID.randomUUID();
        when(inboxRepository.hasConsumed("notification-projection", eventId))
                .thenReturn(true);

        consumer.onEvent(message(
                eventId,
                "{\"documentId\":\"6ed37fd7-39bb-4b81-ae0d-59b7533ed6d0\"}"
        ));

        verifyNoInteractions(projectionService);
        verify(inboxRepository, never())
                .markConsumed("notification-projection", eventId);
        assertCounter("devcollab.worker.event.duplicate_skipped", 1.0);
    }

    @Test
    void marksConsumedOnlyAfterProjectionSucceeds() {
        UUID eventId = UUID.randomUUID();
        when(inboxRepository.hasConsumed("notification-projection", eventId))
                .thenReturn(false);

        consumer.onEvent(message(
                eventId,
                "{\"documentId\":\"6ed37fd7-39bb-4b81-ae0d-59b7533ed6d0\"}"
        ));

        verify(projectionService).project(
                eq(eventId),
                eq("REVIEW_REQUESTED"),
                any(JsonNode.class),
                eq(Instant.parse("2026-07-15T10:00:00Z"))
        );
        verify(inboxRepository).markConsumed("notification-projection", eventId);
        assertCounter("devcollab.worker.event.projected", 1.0);
    }

    @Test
    void doesNotMarkConsumedWhenProjectionFails() {
        UUID eventId = UUID.randomUUID();
        when(inboxRepository.hasConsumed("notification-projection", eventId))
                .thenReturn(false);
        doThrow(new IllegalStateException("database down"))
                .when(projectionService)
                .project(
                        eq(eventId),
                        eq("REVIEW_REQUESTED"),
                        any(JsonNode.class),
                        eq(Instant.parse("2026-07-15T10:00:00Z"))
                );

        assertThrows(IllegalStateException.class, () ->
                consumer.onEvent(message(
                        eventId,
                        "{\"documentId\":\"6ed37fd7-39bb-4b81-ae0d-59b7533ed6d0\"}"
                ))
        );

        verify(inboxRepository, never())
                .markConsumed("notification-projection", eventId);
        assertCounter("devcollab.worker.event.projection_failed", 1.0);
    }

    @Test
    void skipsMalformedMessage() {
        consumer.onEvent("{bad-json");

        verifyNoInteractions(inboxRepository, projectionService);
        assertCounter("devcollab.worker.event.malformed", 1.0);
    }

    @Test
    void skipsMalformedPayload() {
        UUID eventId = UUID.randomUUID();

        consumer.onEvent(message(eventId, "{bad-payload"));

        verifyNoInteractions(inboxRepository, projectionService);
        assertCounter("devcollab.worker.event.payload_malformed", 1.0);
    }

    private void assertCounter(String name, double expectedCount) {
        org.assertj.core.api.Assertions.assertThat(
                meterRegistry.get(name).counter().count()
        ).isEqualTo(expectedCount);
    }

    private static String message(UUID eventId, String payload) {
        return """
                {
                  "eventId": "%s",
                  "aggregateType": "DOCUMENT",
                  "aggregateId": "6ed37fd7-39bb-4b81-ae0d-59b7533ed6d0",
                  "eventType": "REVIEW_REQUESTED",
                  "payload": "%s",
                  "occurredAt": "2026-07-15T10:00:00Z"
                }
                """.formatted(eventId, payload.replace("\"", "\\\""));
    }
}
