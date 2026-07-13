package com.devcollab.knowledgecore.workspace.api;

import com.devcollab.knowledgecore.security.CurrentUser;
import com.devcollab.knowledgecore.workspace.application.InviteWorkspaceMemberCommand;
import com.devcollab.knowledgecore.workspace.application.UpdateWorkspaceMemberRoleCommand;
import com.devcollab.knowledgecore.workspace.application.WorkspaceMemberApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class WorkspaceMemberController {

    private final WorkspaceMemberApplicationService memberService;

    public WorkspaceMemberController(
            WorkspaceMemberApplicationService memberService
    ) {
        this.memberService = memberService;
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/members")
    public List<WorkspaceMemberResponse> list(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        return memberService.listMembers(
                        workspaceId,
                        currentUser.userId()
                )
                .stream()
                .map(WorkspaceMemberResponse::from)
                .toList();
    }

    @PostMapping("/api/v1/workspaces/{workspaceId}/members/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceMemberResponse invite(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody InviteWorkspaceMemberRequest request
    ) {
        return WorkspaceMemberResponse.from(memberService.invite(
                workspaceId,
                currentUser.userId(),
                new InviteWorkspaceMemberCommand(
                        request.username(),
                        request.role()
                )
        ));
    }

    @PatchMapping("/api/v1/workspaces/{workspaceId}/members/{memberUserId}/role")
    public WorkspaceMemberResponse updateRole(
            @PathVariable UUID workspaceId,
            @PathVariable UUID memberUserId,
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody UpdateWorkspaceMemberRoleRequest request
    ) {
        return WorkspaceMemberResponse.from(memberService.updateRole(
                workspaceId,
                memberUserId,
                currentUser.userId(),
                new UpdateWorkspaceMemberRoleCommand(request.role())
        ));
    }

    @DeleteMapping("/api/v1/workspaces/{workspaceId}/members/{memberUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
            @PathVariable UUID workspaceId,
            @PathVariable UUID memberUserId,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        memberService.remove(
                workspaceId,
                memberUserId,
                currentUser.userId()
        );
    }
}
