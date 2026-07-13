package com.devcollab.knowledgecore.common.outbox.infrastructure;

import com.devcollab.knowledgecore.common.outbox.domain.OutboxEvent;
import com.devcollab.knowledgecore.common.outbox.domain.OutboxEventRepository;
import com.devcollab.knowledgecore.common.outbox.domain.OutboxEventStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcOutboxEventRepository implements OutboxEventRepository {

    private static final RowMapper<OutboxEvent> OUTBOX_EVENT_ROW_MAPPER =
            (rs, rowNum) -> new OutboxEvent(
                    rs.getObject("id", UUID.class),
                    rs.getString("aggregate_type"),
                    rs.getObject("aggregate_id", UUID.class),
                    rs.getString("event_type"),
                    rs.getString("payload"),
                    OutboxEventStatus.valueOf(rs.getString("status")),
                    rs.getTimestamp("occurred_at").toInstant(),
                    toInstant(rs.getTimestamp("published_at")),
                    rs.getInt("retry_count"),
                    rs.getString("last_error")
            );

    private final JdbcTemplate jdbcTemplate;

    public JdbcOutboxEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public OutboxEvent save(OutboxEvent event) {
        jdbcTemplate.update("""
                        INSERT INTO outbox_events
                            (id, aggregate_type, aggregate_id, event_type,
                             payload, status, occurred_at, published_at,
                             retry_count, last_error)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                event.id(),
                event.aggregateType(),
                event.aggregateId(),
                event.eventType(),
                event.payload(),
                event.status().name(),
                Timestamp.from(event.occurredAt()),
                event.publishedAt() == null
                        ? null
                        : Timestamp.from(event.publishedAt()),
                event.retryCount(),
                event.lastError());
        return event;
    }

    @Override
    public List<OutboxEvent> findAll() {
        return jdbcTemplate.query("""
                        SELECT * FROM outbox_events
                         ORDER BY occurred_at, id
                        """,
                OUTBOX_EVENT_ROW_MAPPER
        );
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
