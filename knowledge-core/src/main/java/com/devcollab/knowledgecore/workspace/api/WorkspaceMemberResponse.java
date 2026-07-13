package com.devcollab.knowledgecore.workspace.api;

import com.devcollab.knowledgecore.workspace.application.WorkspaceMemberView;
import com.devcollab.knowledgecore.workspace.domain.WorkspaceRole;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceMemberResponse(
        UUID userId,
        String username,
        String displayName,
        WorkspaceRole role,
        Instant joinedAt
) {
    public static WorkspaceMemberResponse from(WorkspaceMemberView member) {
        return new WorkspaceMemberResponse(
                member.userId(),
                member.username(),
                member.displayName(),
                member.role(),
                member.joinedAt()
        );
    }
}
