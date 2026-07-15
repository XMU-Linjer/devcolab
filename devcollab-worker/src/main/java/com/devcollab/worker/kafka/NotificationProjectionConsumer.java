package com.devcollab.worker.kafka;

import com.devcollab.worker.consumerinbox.ConsumerInboxRepository;
import com.devcollab.worker.notification.NotificationProjectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

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
    private final ObjectMapper objectMapper;

    public NotificationProjectionConsumer(
            ConsumerInboxRepository inboxRepository,
            NotificationProjectionService projectionService
    ) {
        this.inboxRepository = inboxRepository;
        this.projectionService = projectionService;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
    }

    @KafkaListener(
            topics = "${devcollab.worker.kafka.topic:devcollab.domain-events}",
            groupId = "${devcollab.worker.notification.group-id:devcollab-notification-projection}"
    )
    public void onEvent(String message) {
        KafkaOutboxMessage event = parseMessage(message);

        if (inboxRepository.hasConsumed(CONSUMER_NAME, event.eventId())) {
            log.debug(
                    "Skipping already-consumed event {} (consumer={})",
                    event.eventId(),
                    CONSUMER_NAME
            );
            return;
        }

        projectionService.project(
                event.eventId(),
                event.eventType(),
                readPayload(event),
                event.occurredAt()
        );

        if (!inboxRepository.markConsumed(CONSUMER_NAME, event.eventId())) {
            log.debug(
                    "Event {} was already marked consumed by another worker",
                    event.eventId()
            );
        }
    }

    private KafkaOutboxMessage parseMessage(String message) {
        try {
            return objectMapper.readValue(message, KafkaOutboxMessage.class);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Failed to deserialize Kafka outbox message",
                    e
            );
        }
    }

    private com.fasterxml.jackson.databind.JsonNode readPayload(
            KafkaOutboxMessage event
    ) {
        try {
            return objectMapper.readTree(event.payload());
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Failed to deserialize Kafka outbox payload",
                    e
            );
        }
    }
}
