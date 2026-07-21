package com.devcollab.knowledgecore.git.domain;

import java.time.Instant;
import java.util.UUID;

public record GitRepository(
        UUID id,
        UUID workspaceId,
        String name,
        GitProvider provider,
        String remoteUrl,
        String defaultBranch,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {
}
