package com.devcollab.knowledgecore.documentchange.api;

import com.devcollab.knowledgecore.documentchange.application.DocumentChangeApplicationService;
import com.devcollab.knowledgecore.documentchange.application.DocumentChangeViews.DetailView;
import com.devcollab.knowledgecore.documentchange.application.DocumentChangeViews.PageView;
import com.devcollab.knowledgecore.documentchange.domain.DocumentChangeModel.Status;
import com.devcollab.knowledgecore.security.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/document-change-requests")
public class DocumentChangeController {

    private final DocumentChangeApplicationService service;

    public DocumentChangeController(DocumentChangeApplicationService service) {
        this.service = service;
    }

    @GetMapping("/pending-count")
    public PendingCountResponse pendingCount(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        return new PendingCountResponse(
                service.pendingCount(workspaceId, currentUser.userId())
        );
    }

    @GetMapping
    public PageView list(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam(defaultValue = "PENDING") Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        return service.list(
                workspaceId,
                currentUser.userId(),
                status,
                page,
                size,
                sort
        );
    }

    @GetMapping("/{requestId}")
    public DetailView detail(
            @PathVariable UUID workspaceId,
            @PathVariable UUID requestId,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        return service.detail(workspaceId, requestId, currentUser.userId());
    }

    public record PendingCountResponse(long count) {
    }
}
