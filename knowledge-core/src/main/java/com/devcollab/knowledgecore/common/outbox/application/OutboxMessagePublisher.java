package com.devcollab.knowledgecore.common.outbox.application;

/**
 * Contract for publishing outbox events to an external message bus.
 *
 * <p>The implementation (Kafka) must ack the message before returning.
 * On failure, the implementation must throw, allowing the Outbox Relay
 * to record {@code retry_count} / {@code last_error} without marking
 * {@code published_at}.
 */
@FunctionalInterface
public interface OutboxMessagePublisher {

    void publish(OutboxKafkaMessage event);
}