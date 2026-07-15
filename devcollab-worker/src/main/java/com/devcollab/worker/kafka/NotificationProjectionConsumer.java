package com.devcollab.worker.kafka;

import com.devcollab.worker.consumerinbox.ConsumerInboxRepository;
import com.devcollab.worker.notification.NotificationProjectionService;
import com.devcollab.worker.observability.WorkerEventMetrics;
import com.fasterxml.jackson.databind.JsonNode;
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
        name = "devcollab.worker.notification.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class NotificationProjectionConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(NotificationProjectionConsumer.class);

    private static final String CONSUMER_NAME = "notification-projection";

    private final ConsumerInboxRepository inboxRepository;
    private final NotificationProjectionService projectionService;
    private final WorkerEventMetrics metrics;
    private final ObjectMapper objectMapper;

    public NotificationProjectionConsumer(
            ConsumerInboxRepository inboxRepository,
            NotificationProjectionService projectionService,
            WorkerEventMetrics metrics
    ) {
        this.inboxRepository = inboxRepository;
        this.projectionService = projectionService;
        this.metrics = metrics;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
    }

    @KafkaListener(
            topics = "${devcollab.worker.kafka.topic:devcollab.domain-events}",
            groupId = "${devcollab.worker.notification.group-id:devcollab-notification-projection}"
    )
    public void onEvent(String message) {
        Optional<KafkaOutboxMessage> parsed = parseMessage(message);
        if (parsed.isEmpty()) {
            return;
        }

        KafkaOutboxMessage event = parsed.get();
        Optional<JsonNode> payload = readPayload(event);
        if (payload.isEmpty()) {
            return;
        }

        if (inboxRepository.hasConsumed(CONSUMER_NAME, event.eventId())) {
            metrics.duplicateSkipped(CONSUMER_NAME, event.eventType());
            log.debug(
                    "Skipping already-consumed event {} (consumer={})",
                    event.eventId(),
                    CONSUMER_NAME
            );
            return;
        }

        try {
            projectionService.project(
                    event.eventId(),
                    event.eventType(),
                    payload.get(),
                    event.occurredAt()
            );
        } catch (RuntimeException e) {
            metrics.projectionFailed(CONSUMER_NAME, event.eventType());
            throw e;
        }

        if (!inboxRepository.markConsumed(CONSUMER_NAME, event.eventId())) {
            log.debug(
                    "Event {} was already marked consumed by another worker",
                    event.eventId()
            );
        }
        metrics.projectionSucceeded(CONSUMER_NAME, event.eventType());
    }

    private Optional<KafkaOutboxMessage> parseMessage(String message) {
        try {
            return Optional.of(objectMapper.readValue(
                    message,
                    KafkaOutboxMessage.class
            ));
        } catch (Exception e) {
            metrics.messageMalformed(CONSUMER_NAME);
            log.warn(
                    "Skipping malformed Kafka outbox message: {}",
                    abbreviate(message)
            );
            log.debug("Malformed Kafka outbox message detail", e);
            return Optional.empty();
        }
    }

    private Optional<JsonNode> readPayload(KafkaOutboxMessage event) {
        try {
            return Optional.of(objectMapper.readTree(event.payload()));
        } catch (Exception e) {
            metrics.payloadMalformed(CONSUMER_NAME, event.eventType());
            log.warn(
                    "Skipping Kafka outbox event with malformed payload: eventId={} payload={}",
                    event.eventId(),
                    abbreviate(event.payload())
            );
            log.debug("Malformed Kafka outbox payload detail", e);
            return Optional.empty();
        }
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "<null>";
        }
        return value.length() <= 200
                ? value
                : value.substring(0, 200) + "...";
    }
}
