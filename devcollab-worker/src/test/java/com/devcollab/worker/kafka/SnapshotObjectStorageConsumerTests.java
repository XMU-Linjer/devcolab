package com.devcollab.worker.kafka;

import com.devcollab.worker.consumerinbox.ConsumerInboxRepository;
import com.devcollab.worker.objectstorage.SnapshotProjectionService;
import com.devcollab.worker.observability.WorkerEventMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SnapshotObjectStorageConsumerTests {

    private final ConsumerInboxRepository inboxRepository =
            mock(ConsumerInboxRepository.class);
    private final SnapshotProjectionService projectionService =
            mock(SnapshotProjectionService.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final SnapshotObjectStorageConsumer consumer =
            new SnapshotObjectStorageConsumer(
                    inboxRepository,
                    projectionService,
                    new WorkerEventMetrics(meterRegistry)
            );

    @Test
    void ignoresUnrelatedDocumentEvent() {
        consumer.onEvent(message(UUID.randomUUID(), "DOCUMENT_UPDATED"));

        verifyNoInteractions(inboxRepository, projectionService);
    }

    @Test
    void skipsAlreadyConsumedSnapshotEvent() {
        UUID eventId = UUID.randomUUID();
        when(inboxRepository.hasConsumed("snapshot-object-storage", eventId))
                .thenReturn(true);

        consumer.onEvent(message(eventId, "SNAPSHOT_REQUESTED"));

        verifyNoInteractions(projectionService);
        verify(inboxRepository, never())
                .markConsumed("snapshot-object-storage", eventId);
        assertCounter("devcollab.worker.event.duplicate_skipped", 1.0);
    }

    @Test
    void marksInboxOnlyAfterSnapshotWasStored() {
        UUID eventId = UUID.randomUUID();

        consumer.onEvent(message(eventId, "SNAPSHOT_REQUESTED"));

        verify(projectionService).project(eventId, payload());
        verify(inboxRepository)
                .markConsumed("snapshot-object-storage", eventId);
        assertCounter("devcollab.worker.event.projected", 1.0);
    }

    @Test
    void leavesInboxUnchangedWhenSnapshotStorageFails() {
        UUID eventId = UUID.randomUUID();
        doThrow(new IllegalStateException("minio down"))
                .when(projectionService).project(eventId, payload());

        assertThrows(IllegalStateException.class, () ->
                consumer.onEvent(message(eventId, "SNAPSHOT_REQUESTED"))
        );

        verify(inboxRepository, never())
                .markConsumed("snapshot-object-storage", eventId);
        assertCounter("devcollab.worker.event.projection_failed", 1.0);
    }

    @Test
    void skipsMalformedMessage() {
        consumer.onEvent("{bad-json");

        verifyNoInteractions(inboxRepository, projectionService);
        assertCounter("devcollab.worker.event.malformed", 1.0);
    }

    private void assertCounter(String name, double expected) {
        assertThat(meterRegistry.get(name).counter().count())
                .isEqualTo(expected);
    }

    private static String message(UUID eventId, String eventType) {
        return """
                {
                  "eventId": "%s",
                  "aggregateType": "DOCUMENT",
                  "aggregateId": "6ed37fd7-39bb-4b81-ae0d-59b7533ed6d0",
                  "eventType": "%s",
                  "payload": "%s",
                  "occurredAt": "2026-07-17T01:00:00Z"
                }
                """.formatted(
                eventId,
                eventType,
                payload().replace("\"", "\\\"")
        );
    }

    private static String payload() {
        return """
                {"workspaceId":"c7af63d0-993c-4f6a-a130-58da8ff793c8","documentId":"6ed37fd7-39bb-4b81-ae0d-59b7533ed6d0","versionId":"82a078b1-428c-4a4c-a3e3-bc42af95db6e","versionNo":2}
                """.strip();
    }
}
