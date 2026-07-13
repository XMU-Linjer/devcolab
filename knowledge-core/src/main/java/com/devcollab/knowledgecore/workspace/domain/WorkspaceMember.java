package com.devcollab.knowledgecore.workspace.domain;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceMember(
        UUID workspaceId,
        UUID userId,
        WorkspaceRole role,
        Instant joinedAt
) {
}
