package com.devcollab.knowledgecore.document.domain;

import java.time.Instant;
import java.util.UUID;

public record DocumentBlock(
        UUID id,
        UUID documentId,
        DocumentBlockType type,
        String text,
        int sortOrder,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {
}
