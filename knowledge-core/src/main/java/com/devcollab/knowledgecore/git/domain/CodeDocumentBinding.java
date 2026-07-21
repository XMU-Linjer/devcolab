package com.devcollab.knowledgecore.git.domain;

import java.time.Instant;
import java.util.UUID;

public record CodeDocumentBinding(
        UUID id,
        UUID workspaceId,
        UUID repositoryId,
        UUID documentId,
        UUID blockId,
        String pathPattern,
        UUID createdBy,
        Instant createdAt
) {
}
