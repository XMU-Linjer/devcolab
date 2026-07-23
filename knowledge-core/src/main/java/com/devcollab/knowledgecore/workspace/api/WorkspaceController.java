package com.devcollab.knowledgecore.workspace.api;

import com.devcollab.knowledgecore.security.CurrentUser;
import com.devcollab.knowledgecore.workspace.application.CreateWorkspaceCommand;
import com.devcollab.knowledgecore.workspace.application.WorkspaceApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {

    private final WorkspaceApplicationService workspaceService;

    public WorkspaceController(
            WorkspaceApplicationService workspaceService
    ) {
        this.workspaceService = workspaceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceResponse create(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody CreateWorkspaceRequest request
    ) {
        return WorkspaceResponse.from(workspaceService.create(
                currentUser.userId(),
                new CreateWorkspaceCommand(request.name())
        ));
    }

    @GetMapping
    public List<WorkspaceResponse> list(
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        return workspaceService.listForUser(currentUser.userId()).stream()
                .map(WorkspaceResponse::from)
                .toList();
    }

    @GetMapping("/{workspaceId}")
    public WorkspaceResponse get(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        return WorkspaceResponse.from(
                workspaceService.get(workspaceId, currentUser.userId())
        );
    }

    @DeleteMapping("/{workspaceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        workspaceService.delete(workspaceId, currentUser.userId());
    }
}
