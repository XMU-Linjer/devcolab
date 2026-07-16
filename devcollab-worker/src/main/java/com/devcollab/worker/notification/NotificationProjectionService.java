package com.devcollab.worker.notification;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationProjectionService {

    private static final Logger log =
            LoggerFactory.getLogger(NotificationProjectionService.class);

    private final JdbcTemplate jdbcTemplate;

    public NotificationProjectionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void project(
            UUID eventId,
            String eventType,
            JsonNode payload,
            Instant occurredAt
    ) {
        switch (eventType) {
            case "REVIEW_REQUESTED", "DOCUMENT_REVIEW_SUBMITTED" ->
                    notifyAdminsForReviewRequested(
                            eventId,
                            eventType,
                            payload,
                            occurredAt
                    );
            case "REVIEW_COMPLETED", "DOCUMENT_REVIEW_APPROVED" ->
                    notifyDocumentAuthorReviewCompleted(
                            eventId,
                            eventType,
                            payload,
                            occurredAt
                    );
            case "REVIEW_FAILED", "DOCUMENT_REVIEW_REJECTED" ->
                    notifyDocumentAuthorReviewFailed(
                            eventId,
                            eventType,
                            payload,
                            occurredAt
                    );
            case "REVIEW_ISSUE_CREATED" ->
                    notifyReviewIssueAssignee(
                            eventId,
                            eventType,
                            payload,
                            occurredAt
                    );
            case "REVIEW_ISSUE_RESOLVED",
                 "REVIEW_ISSUE_ACCEPTED",
                 "REVIEW_ISSUE_REJECTED" ->
                    notifyReviewIssueCreator(
                            eventId,
                            eventType,
                            payload,
                            occurredAt
                    );
            case "NOTIFICATION_REQUESTED" ->
                    notifyRequestedRecipient(
                            eventId,
                            eventType,
                            payload,
                            occurredAt
                    );
            default -> log.debug(
                    "Notification projection ignored event type={}",
                    eventType
            );
        }
    }

    private void notifyAdminsForReviewRequested(
            UUID eventId,
            String eventType,
            JsonNode payload,
            Instant occurredAt
    ) {
        UUID workspaceId = uuid(payload, "workspaceId");
        UUID documentId = uuid(payload, "documentId");
        UUID operatorUserId = uuid(payload, "operatorUserId");
        String title = text(payload, "title", "未命名文档");

        List<UUID> recipients = jdbcTemplate.query("""
                        SELECT user_id FROM workspace_members
                         WHERE workspace_id = ? AND role = 'ADMIN'
                        """,
                (rs, rowNum) -> rs.getObject("user_id", UUID.class),
                workspaceId
        );

        for (UUID recipient : recipients) {
            if (!recipient.equals(operatorUserId)) {
                insertNotification(
                        recipient,
                        workspaceId,
                        documentId,
                        eventType,
                        "文档待评审：" + title,
                        "有文档提交评审，请进入工作台处理。",
                        eventId,
                        occurredAt
                );
            }
        }
    }

    private void notifyDocumentAuthorReviewCompleted(
            UUID eventId,
            String eventType,
            JsonNode payload,
            Instant occurredAt
    ) {
        UUID workspaceId = uuid(payload, "workspaceId");
        UUID documentId = uuid(payload, "documentId");
        UUID operatorUserId = uuid(payload, "operatorUserId");
        UUID authorId = findDocumentAuthor(documentId);
        if (authorId == null || authorId.equals(operatorUserId)) {
            return;
        }

        String title = text(payload, "title", "未命名文档");
        insertNotification(
                authorId,
                workspaceId,
                documentId,
                eventType,
                "文档已发布：" + title,
                "你的文档已通过评审并发布。",
                eventId,
                occurredAt
        );
    }

    private void notifyDocumentAuthorReviewFailed(
            UUID eventId,
            String eventType,
            JsonNode payload,
            Instant occurredAt
    ) {
        UUID workspaceId = uuid(payload, "workspaceId");
        UUID documentId = uuid(payload, "documentId");
        UUID operatorUserId = uuid(payload, "operatorUserId");
        UUID authorId = findDocumentAuthor(documentId);
        if (authorId == null || authorId.equals(operatorUserId)) {
            return;
        }

        String title = text(payload, "title", "未命名文档");
        insertNotification(
                authorId,
                workspaceId,
                documentId,
                eventType,
                "文档被驳回：" + title,
                "你的文档评审未通过，请根据评审意见修改。",
                eventId,
                occurredAt
        );
    }

    private void notifyReviewIssueAssignee(
            UUID eventId,
            String eventType,
            JsonNode payload,
            Instant occurredAt
    ) {
        UUID recipient = nullableUuid(payload, "assigneeId");
        UUID operator = uuid(payload, "operatorUserId");
        if (recipient == null || recipient.equals(operator)) {
            return;
        }

        insertNotification(
                recipient,
                uuid(payload, "workspaceId"),
                uuid(payload, "documentId"),
                eventType,
                "新的评审问题：" + text(payload, "title", "未命名问题"),
                "你被分配了一个 Review Issue，请进入工作台处理。",
                eventId,
                occurredAt
        );
    }

    private void notifyReviewIssueCreator(
            UUID eventId,
            String eventType,
            JsonNode payload,
            Instant occurredAt
    ) {
        UUID recipient = uuid(payload, "createdBy");
        UUID operator = uuid(payload, "operatorUserId");
        if (recipient.equals(operator)) {
            return;
        }

        insertNotification(
                recipient,
                uuid(payload, "workspaceId"),
                uuid(payload, "documentId"),
                eventType,
                "评审问题状态变更：" + text(payload, "title", "未命名问题"),
                "你创建的 Review Issue 状态已更新为 "
                        + text(payload, "status", "UNKNOWN") + "。",
                eventId,
                occurredAt
        );
    }

    private void notifyRequestedRecipient(
            UUID eventId,
            String eventType,
            JsonNode payload,
            Instant occurredAt
    ) {
        UUID recipient = nullableUuid(payload, "recipientUserId");
        if (recipient == null) {
            log.warn(
                    "NOTIFICATION_REQUESTED ignored because recipientUserId is missing: event={}",
                    eventId
            );
            return;
        }

        insertNotification(
                recipient,
                uuid(payload, "workspaceId"),
                nullableUuid(payload, "documentId"),
                eventType,
                text(payload, "title", "系统通知"),
                text(payload, "content", ""),
                eventId,
                occurredAt
        );
    }

    private UUID findDocumentAuthor(UUID documentId) {
        List<UUID> authors = jdbcTemplate.query("""
                        SELECT created_by FROM documents WHERE id = ?
                        """,
                (rs, rowNum) -> rs.getObject("created_by", UUID.class),
                documentId
        );
        return authors.isEmpty() ? null : authors.get(0);
    }

    private void insertNotification(
            UUID recipientUserId,
            UUID workspaceId,
            UUID documentId,
            String type,
            String title,
            String content,
            UUID sourceEventId,
            Instant createdAt
    ) {
        try {
            jdbcTemplate.update("""
                            INSERT INTO notifications
                                (id, recipient_user_id, workspace_id,
                                 document_id, type, title, content,
                                 source_event_id, read_at, created_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, ?)
                            """,
                    UUID.randomUUID(),
                    recipientUserId,
                    workspaceId,
                    documentId,
                    type,
                    title,
                    content,
                    sourceEventId,
                    Timestamp.from(createdAt)
            );
        } catch (DuplicateKeyException ignored) {
            log.debug(
                    "Notification already exists: recipient={} event={}",
                    recipientUserId,
                    sourceEventId
            );
        }
    }

    private UUID uuid(JsonNode payload, String field) {
        UUID value = nullableUuid(payload, field);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Notification payload missing UUID field: " + field
            );
        }
        return value;
    }

    private UUID nullableUuid(JsonNode payload, String field) {
        JsonNode node = payload.get(field);
        if (node == null || node.isNull() || node.asText().isBlank()) {
            return null;
        }
        return UUID.fromString(node.asText());
    }

    private String text(JsonNode payload, String field, String fallback) {
        JsonNode node = payload.get(field);
        if (node == null || node.isNull() || node.asText().isBlank()) {
            return fallback;
        }
        return node.asText();
    }
}
