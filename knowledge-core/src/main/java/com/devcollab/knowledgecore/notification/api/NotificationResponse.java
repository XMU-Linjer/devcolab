package com.devcollab.knowledgecore.notification.api;

import com.devcollab.knowledgecore.notification.domain.Notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID workspaceId,
        UUID documentId,
        String type,
        String title,
        String content,
        boolean unread,
        Instant readAt,
        Instant createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.id(),
                notification.workspaceId(),
                notification.documentId(),
                notification.type(),
                notification.title(),
                notification.content(),
                notification.unread(),
                notification.readAt(),
                notification.createdAt()
        );
    }
}
