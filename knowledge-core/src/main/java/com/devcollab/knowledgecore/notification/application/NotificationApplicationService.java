package com.devcollab.knowledgecore.notification.application;

import com.devcollab.knowledgecore.notification.application.exception.NotificationNotFoundException;
import com.devcollab.knowledgecore.notification.domain.Notification;
import com.devcollab.knowledgecore.notification.domain.NotificationRepository;
import com.devcollab.knowledgecore.workspace.application.exception.WorkspaceAccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationApplicationService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    private final NotificationRepository notificationRepository;

    public NotificationApplicationService(
            NotificationRepository notificationRepository
    ) {
        this.notificationRepository = notificationRepository;
    }

    public List<Notification> list(
            UUID currentUserId,
            boolean unreadOnly,
            Integer limit
    ) {
        return notificationRepository.findAllByRecipientUserId(
                currentUserId,
                unreadOnly,
                normalizeLimit(limit)
        );
    }

    @Transactional
    public Notification markRead(UUID notificationId, UUID currentUserId) {
        Notification notification = notificationRepository.findById(
                notificationId
        ).orElseThrow(NotificationNotFoundException::new);
        if (!notification.recipientUserId().equals(currentUserId)) {
            throw new WorkspaceAccessDeniedException();
        }
        if (!notification.unread()) {
            return notification;
        }
        Notification read = new Notification(
                notification.id(),
                notification.recipientUserId(),
                notification.workspaceId(),
                notification.documentId(),
                notification.type(),
                notification.title(),
                notification.content(),
                notification.sourceEventId(),
                Instant.now(),
                notification.createdAt()
        );
        return notificationRepository.save(read);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
