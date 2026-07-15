package com.devcollab.knowledgecore.notification.infrastructure;

import com.devcollab.knowledgecore.notification.domain.Notification;
import com.devcollab.knowledgecore.notification.domain.NotificationRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcNotificationRepository implements NotificationRepository {

    private static final RowMapper<Notification> ROW_MAPPER =
            (rs, rowNum) -> new Notification(
                    rs.getObject("id", UUID.class),
                    rs.getObject("recipient_user_id", UUID.class),
                    rs.getObject("workspace_id", UUID.class),
                    rs.getObject("document_id", UUID.class),
                    rs.getString("type"),
                    rs.getString("title"),
                    rs.getString("content"),
                    rs.getObject("source_event_id", UUID.class),
                    rs.getTimestamp("read_at") == null
                            ? null
                            : rs.getTimestamp("read_at").toInstant(),
                    rs.getTimestamp("created_at").toInstant()
            );

    private final JdbcTemplate jdbcTemplate;

    public JdbcNotificationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Notification> findAllByRecipientUserId(
            UUID recipientUserId,
            boolean unreadOnly,
            int limit
    ) {
        if (unreadOnly) {
            return jdbcTemplate.query("""
                            SELECT * FROM notifications
                             WHERE recipient_user_id = ? AND read_at IS NULL
                             ORDER BY created_at DESC
                             LIMIT ?
                            """,
                    ROW_MAPPER,
                    recipientUserId,
                    limit
            );
        }
        return jdbcTemplate.query("""
                        SELECT * FROM notifications
                         WHERE recipient_user_id = ?
                         ORDER BY created_at DESC
                         LIMIT ?
                        """,
                ROW_MAPPER,
                recipientUserId,
                limit
        );
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT * FROM notifications WHERE id = ?",
                ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    @Override
    public Notification save(Notification notification) {
        int updated = jdbcTemplate.update("""
                        UPDATE notifications
                           SET read_at = ?
                         WHERE id = ?
                        """,
                notification.readAt() == null
                        ? null
                        : Timestamp.from(notification.readAt()),
                notification.id()
        );

        if (updated == 0) {
            jdbcTemplate.update("""
                            INSERT INTO notifications
                                (id, recipient_user_id, workspace_id,
                                 document_id, type, title, content,
                                 source_event_id, read_at, created_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    notification.id(),
                    notification.recipientUserId(),
                    notification.workspaceId(),
                    notification.documentId(),
                    notification.type(),
                    notification.title(),
                    notification.content(),
                    notification.sourceEventId(),
                    notification.readAt() == null
                            ? null
                            : Timestamp.from(notification.readAt()),
                    Timestamp.from(notification.createdAt())
            );
        }
        return notification;
    }
}
