package com.devcollab.knowledgecore.document.domain;

import java.time.Instant;
import java.util.UUID;

public record Document(
        UUID id,
        UUID workspaceId,
        UUID parentDocumentId,
        String title,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {
}
