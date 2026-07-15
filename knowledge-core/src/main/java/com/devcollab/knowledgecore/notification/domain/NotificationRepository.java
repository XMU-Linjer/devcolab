package com.devcollab.knowledgecore.notification.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {

    List<Notification> findAllByRecipientUserId(
            UUID recipientUserId,
            boolean unreadOnly,
            int limit
    );

    Optional<Notification> findById(UUID id);

    Notification save(Notification notification);
}
