package com.devcollab.worker.kafka;

import com.devcollab.worker.consumerinbox.ConsumerInboxRepository;
import com.devcollab.worker.objectstorage.SnapshotProjectionService;
import com.devcollab.worker.observability.WorkerEventMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@ConditionalOnProperty(
        name = {
                "devcollab.worker.snapshot.enabled",
                "devcollab.object-storage.minio.enabled"
        },
        havingValue = "true"
)
public class SnapshotObjectStorageConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(SnapshotObjectStorageConsumer.class);
    private static final String CONSUMER_NAME = "snapshot-object-storage";
    private static final String EVENT_TYPE = "SNAPSHOT_REQUESTED";

    private final ConsumerInboxRepository inboxRepository;
    private final SnapshotProjectionService projectionService;
    private final WorkerEventMetrics metrics;
    private final ObjectMapper objectMapper;

    public SnapshotObjectStorageConsumer(
            ConsumerInboxRepository inboxRepository,
            SnapshotProjectionService projectionService,
            WorkerEventMetrics metrics
    ) {
        this.inboxRepository = inboxRepository;
        this.projectionService = projectionService;
        this.metrics = metrics;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
    }

    @KafkaListener(
            topics = "${devcollab.worker.kafka.document-topic:devcollab.document.events}",
            groupId = "${devcollab.worker.snapshot.group-id:devcollab-snapshot-object-storage}"
    )
    public void onEvent(String message) {
        Optional<KafkaOutboxMessage> parsed = parseMessage(message);
        if (parsed.isEmpty() || !EVENT_TYPE.equals(parsed.get().eventType())) {
            return;
        }

        KafkaOutboxMessage event = parsed.get();
        if (inboxRepository.hasConsumed(CONSUMER_NAME, event.eventId())) {
            metrics.duplicateSkipped(CONSUMER_NAME, event.eventType());
            return;
        }

        try {
            projectionService.project(event.eventId(), event.payload());
        } catch (RuntimeException exception) {
            metrics.projectionFailed(CONSUMER_NAME, event.eventType());
            log.error(
                    "Snapshot projection failed eventId={} aggregateId={}",
                    event.eventId(),
                    event.aggregateId(),
                    exception
            );
            throw exception;
        }

        inboxRepository.markConsumed(CONSUMER_NAME, event.eventId());
        metrics.projectionSucceeded(CONSUMER_NAME, event.eventType());
        log.info(
                "Snapshot event projected eventId={} aggregateId={}",
                event.eventId(),
                event.aggregateId()
        );
    }

    private Optional<KafkaOutboxMessage> parseMessage(String message) {
        try {
            return Optional.of(objectMapper.readValue(
                    message, KafkaOutboxMessage.class
            ));
        } catch (Exception exception) {
            metrics.messageMalformed(CONSUMER_NAME);
            log.warn("Skipping malformed snapshot Kafka message");
            log.debug("Malformed snapshot Kafka message detail", exception);
            return Optional.empty();
        }
    }
}
