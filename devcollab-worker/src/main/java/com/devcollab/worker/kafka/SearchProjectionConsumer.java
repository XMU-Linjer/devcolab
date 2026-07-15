package com.devcollab.worker.kafka;

import com.devcollab.worker.consumerinbox.ConsumerInboxRepository;
import com.devcollab.worker.observability.WorkerEventMetrics;
import com.devcollab.worker.search.projection.DocProjectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Kafka consumer for search projection.
 *
 * <p>Consumes {@code devcollab.domain-events} and projects document mutations
 * into Elasticsearch. Uses {@code consumer_inbox} table for idempotency after
 * the projection side effect succeeds.
 */
@Component
@ConditionalOnProperty(
        name = "devcollab.search.elasticsearch.enabled",
        havingValue = "true"
)
public class SearchProjectionConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(SearchProjectionConsumer.class);

    private static final String CONSUMER_NAME = "search-projection";

    private final ConsumerInboxRepository inboxRepository;
    private final DocProjectionService projectionService;
    private final WorkerEventMetrics metrics;
    private final ObjectMapper objectMapper;

    public SearchProjectionConsumer(
            ConsumerInboxRepository inboxRepository,
            DocProjectionService projectionService,
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
            groupId = "${devcollab.worker.search.group-id:devcollab-search-projection}"
    )
    public void onEvent(String message) {
        Optional<KafkaOutboxMessage> parsed = parseMessage(message);
        if (parsed.isEmpty()) {
            return;
        }

        KafkaOutboxMessage event = parsed.get();

        if (inboxRepository.hasConsumed(CONSUMER_NAME, event.eventId())) {
            metrics.duplicateSkipped(CONSUMER_NAME, event.eventType());
            log.debug(
                    "Skipping already-consumed event {} (consumer={})",
                    event.eventId(),
                    CONSUMER_NAME
            );
            return;
        }

        log.debug(
                "Processing outbox event {} type={} aggregate={}/{}",
                event.eventId(),
                event.eventType(),
                event.aggregateType(),
                event.aggregateId()
        );

        try {
            projectionService.project(
                    event.eventId(),
                    event.eventType(),
                    event.payload()
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

    private String abbreviate(String value) {
        if (value == null) {
            return "<null>";
        }
        return value.length() <= 200
                ? value
                : value.substring(0, 200) + "...";
    }
}
