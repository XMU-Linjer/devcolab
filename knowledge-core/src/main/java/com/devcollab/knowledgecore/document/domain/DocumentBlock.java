package com.devcollab.knowledgecore.document.domain;

import java.time.Instant;
import java.util.UUID;

public record DocumentBlock(
        UUID id,
        UUID documentId,
        DocumentBlockType type,
        String text,
        int sortOrder,
        long version,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {
    public DocumentBlock updateText(String newText, Instant now) {
        return new DocumentBlock(
                id,
                documentId,
                type,
                newText,
                sortOrder,
                version + 1,
                createdBy,
                createdAt,
                now
        );
    }

    public DocumentBlock changeSortOrder(int newSortOrder, Instant now) {
        return new DocumentBlock(
                id,
                documentId,
                type,
                text,
                newSortOrder,
                version + 1,
                createdBy,
                createdAt,
                now
        );
    }
}
