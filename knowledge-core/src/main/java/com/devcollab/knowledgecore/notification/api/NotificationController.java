package com.devcollab.knowledgecore.notification.api;

import com.devcollab.knowledgecore.notification.application.NotificationApplicationService;
import com.devcollab.knowledgecore.security.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class NotificationController {

    private final NotificationApplicationService notificationService;

    public NotificationController(
            NotificationApplicationService notificationService
    ) {
        this.notificationService = notificationService;
    }

    @GetMapping("/api/v1/notifications")
    public List<NotificationResponse> list(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) Integer limit
    ) {
        return notificationService.list(
                        currentUser.userId(),
                        unreadOnly,
                        limit
                )
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @PatchMapping("/api/v1/notifications/{notificationId}/read")
    public NotificationResponse markRead(
            @PathVariable UUID notificationId,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        return NotificationResponse.from(notificationService.markRead(
                notificationId,
                currentUser.userId()
        ));
    }
}
