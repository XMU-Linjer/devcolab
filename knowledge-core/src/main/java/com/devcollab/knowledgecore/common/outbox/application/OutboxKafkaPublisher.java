package com.devcollab.knowledgecore.common.outbox.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

/**
 * Publishes outbox events to Kafka.
 *
 * <p>This is the only exit point out of Core. There is no fallback to local
 * handlers when Kafka is unavailable: events stay in {@code outbox_events}
 * with FAILED status and are retried by the Outbox Relay.
 */
@Component
public class OutboxKafkaPublisher implements OutboxMessagePublisher {

    private static final Logger log =
            LoggerFactory.getLogger(OutboxKafkaPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final ObjectMapper objectMapper;

    public OutboxKafkaPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${devcollab.outbox.kafka.topic:devcollab.domain-events}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
    }

    /**
     * Publishes a single outbox event to Kafka synchronously.
     * Kafka ACK must succeed before the caller marks published_at.
     *
     * @param event the outbox event to publish
     * @throws RuntimeException wrapping the original failure if Kafka send fails
     */
    @Override
    public void publish(OutboxKafkaMessage event) {
        String key = event.kafkaKey();
        String value;
        try {
            value = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Failed to serialize outbox event to JSON: eventId="
                            + event.eventId(),
                    e
            );
        }

        try {
            var future = kafkaTemplate.send(topic, key, value);
            future.get();
            log.debug(
                    "Outbox event {} published to Kafka topic {}",
                    event.eventId(),
                    topic
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(
                    "Kafka send interrupted for event " + event.eventId(),
                    e
            );
        } catch (ExecutionException e) {
            throw new RuntimeException(
                    "Kafka send failed for event " + event.eventId(),
                    e.getCause()
            );
        }
    }
}
