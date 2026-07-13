package com.devcollab.knowledgecore.workspace.application;

import com.devcollab.knowledgecore.auth.domain.UserAccount;
import com.devcollab.knowledgecore.workspace.domain.WorkspaceMember;
import com.devcollab.knowledgecore.workspace.domain.WorkspaceRole;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceMemberView(
        UUID userId,
        String username,
        String displayName,
        WorkspaceRole role,
        Instant joinedAt
) {
    public static WorkspaceMemberView from(
            WorkspaceMember member,
            UserAccount user
    ) {
        return new WorkspaceMemberView(
                member.userId(),
                user.username(),
                user.displayName(),
                member.role(),
                member.joinedAt()
        );
    }
}
