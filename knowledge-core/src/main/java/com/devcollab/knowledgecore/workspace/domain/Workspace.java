package com.devcollab.knowledgecore.workspace.domain;

import java.time.Instant;
import java.util.UUID;

public record Workspace(
        UUID id,
        String name,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {
}
