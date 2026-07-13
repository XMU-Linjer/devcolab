package com.devcollab.knowledgecore.workspace.application;

import com.devcollab.knowledgecore.workspace.domain.Workspace;
import com.devcollab.knowledgecore.workspace.domain.WorkspaceRole;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceView(
        UUID id,
        String name,
        WorkspaceRole currentUserRole,
        Instant createdAt,
        Instant updatedAt
) {
    public static WorkspaceView from(
            Workspace workspace,
            WorkspaceRole role
    ) {
        return new WorkspaceView(
                workspace.id(),
                workspace.name(),
                role,
                workspace.createdAt(),
                workspace.updatedAt()
        );
    }
}
