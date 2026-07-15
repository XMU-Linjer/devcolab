package com.devcollab.worker.kafka;

import com.devcollab.worker.consumerinbox.ConsumerInboxRepository;
import com.devcollab.worker.observability.WorkerEventMetrics;
import com.devcollab.worker.search.projection.DocProjectionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SearchProjectionConsumerTests {

    private final ConsumerInboxRepository inboxRepository =
            mock(ConsumerInboxRepository.class);
    private final DocProjectionService projectionService =
            mock(DocProjectionService.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final WorkerEventMetrics metrics =
            new WorkerEventMetrics(meterRegistry);
    private final SearchProjectionConsumer consumer =
            new SearchProjectionConsumer(
                    inboxRepository,
                    projectionService,
                    metrics
            );

    @Test
    void skipsAlreadyConsumedEvent() {
        UUID eventId = UUID.randomUUID();
        when(inboxRepository.hasConsumed("search-projection", eventId))
                .thenReturn(true);

        consumer.onEvent(message(eventId));

        verifyNoInteractions(projectionService);
        verify(inboxRepository, never())
                .markConsumed("search-projection", eventId);
        assertCounter("devcollab.worker.event.duplicate_skipped", 1.0);
    }

    @Test
    void marksConsumedOnlyAfterProjectionSucceeds() {
        UUID eventId = UUID.randomUUID();
        when(inboxRepository.hasConsumed("search-projection", eventId))
                .thenReturn(false);

        consumer.onEvent(message(eventId));

        verify(projectionService).project(
                eventId,
                "DOCUMENT_UPDATED",
                "{\"documentId\":\"6ed37fd7-39bb-4b81-ae0d-59b7533ed6d0\"}"
        );
        verify(inboxRepository).markConsumed("search-projection", eventId);
        assertCounter("devcollab.worker.event.projected", 1.0);
    }

    @Test
    void doesNotMarkConsumedWhenProjectionFails() {
        UUID eventId = UUID.randomUUID();
        when(inboxRepository.hasConsumed("search-projection", eventId))
                .thenReturn(false);
        doThrow(new IllegalStateException("es down"))
                .when(projectionService)
                .project(
                        eventId,
                        "DOCUMENT_UPDATED",
                        "{\"documentId\":\"6ed37fd7-39bb-4b81-ae0d-59b7533ed6d0\"}"
                );

        assertThrows(IllegalStateException.class, () ->
                consumer.onEvent(message(eventId))
        );

        verify(inboxRepository, never())
                .markConsumed("search-projection", eventId);
        assertCounter("devcollab.worker.event.projection_failed", 1.0);
    }

    @Test
    void skipsMalformedMessage() {
        consumer.onEvent("{bad-json");

        verifyNoInteractions(inboxRepository, projectionService);
        assertCounter("devcollab.worker.event.malformed", 1.0);
    }

    private void assertCounter(String name, double expectedCount) {
        org.assertj.core.api.Assertions.assertThat(
                meterRegistry.get(name).counter().count()
        ).isEqualTo(expectedCount);
    }

    private static String message(UUID eventId) {
        return """
                {
                  "eventId": "%s",
                  "aggregateType": "DOCUMENT",
                  "aggregateId": "6ed37fd7-39bb-4b81-ae0d-59b7533ed6d0",
                  "eventType": "DOCUMENT_UPDATED",
                  "payload": "{\\"documentId\\":\\"6ed37fd7-39bb-4b81-ae0d-59b7533ed6d0\\"}",
                  "occurredAt": "2026-07-15T10:00:00Z"
                }
                """.formatted(eventId);
    }
}
