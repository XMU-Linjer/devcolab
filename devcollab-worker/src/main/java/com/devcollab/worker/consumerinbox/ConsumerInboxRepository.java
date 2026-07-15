package com.devcollab.worker.consumerinbox;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Idempotent consumer deduplication via {@code consumer_inbox} table.
 *
 * <p>The consumer checks whether an event was already consumed before
 * executing the side effect, and records the event only after the side effect
 * succeeds. This keeps failed side effects retryable.
 */
@Repository
public class ConsumerInboxRepository {

    private final JdbcTemplate jdbcTemplate;

    public ConsumerInboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean hasConsumed(String consumerName, UUID eventId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM consumer_inbox
                WHERE consumer_name = ? AND event_id = ?
                """,
                Integer.class,
                consumerName,
                eventId
        );
        return count != null && count > 0;
    }

    public boolean markConsumed(String consumerName, UUID eventId) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO consumer_inbox (id, consumer_name, event_id)
                    VALUES (?, ?, ?)
                    """,
                    UUID.randomUUID(),
                    consumerName,
                    eventId
            );
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }
}
