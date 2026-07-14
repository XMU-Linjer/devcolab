package com.devcollab.knowledgecore.document.domain;

import java.time.Instant;
import java.util.UUID;

public record Document(
        UUID id,
        UUID workspaceId,
        UUID parentDocumentId,
        String title,
        DocumentType documentType,
        DocumentReviewStatus reviewStatus,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {
}
