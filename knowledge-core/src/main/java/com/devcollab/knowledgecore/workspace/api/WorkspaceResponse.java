package com.devcollab.knowledgecore.workspace.api;

import com.devcollab.knowledgecore.workspace.application.WorkspaceView;
import com.devcollab.knowledgecore.workspace.domain.WorkspaceRole;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceResponse(
        UUID id,
        String name,
        WorkspaceRole currentUserRole,
        Instant createdAt,
        Instant updatedAt
) {
    public static WorkspaceResponse from(WorkspaceView workspace) {
        return new WorkspaceResponse(
                workspace.id(),
                workspace.name(),
                workspace.currentUserRole(),
                workspace.createdAt(),
                workspace.updatedAt()
        );
    }
}
