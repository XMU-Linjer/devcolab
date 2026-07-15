package com.devcollab.knowledgecore.notification.domain;

import java.time.Instant;
import java.util.UUID;

public record Notification(
        UUID id,
        UUID recipientUserId,
        UUID workspaceId,
        UUID documentId,
        String type,
        String title,
        String content,
        UUID sourceEventId,
        Instant readAt,
        Instant createdAt
) {
    public boolean unread() {
        return readAt == null;
    }
}
