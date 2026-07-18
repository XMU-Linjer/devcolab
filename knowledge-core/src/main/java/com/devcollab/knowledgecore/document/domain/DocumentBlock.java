package com.devcollab.knowledgecore.document.domain;

import java.time.Instant;
import java.util.UUID;

public record DocumentBlock(
        UUID id,
        UUID documentId,
        DocumentBlockType type,
        String text,
        int contentSchemaVersion,
        String contentJson,
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
                contentSchemaVersion,
                contentJson,
                sortOrder,
                version + 1,
                createdBy,
                createdAt,
                now
        );
    }

    public DocumentBlock updateContent(
            String newText,
            int newContentSchemaVersion,
            String newContentJson,
            Instant now
    ) {
        return new DocumentBlock(
                id,
                documentId,
                type,
                newText,
                newContentSchemaVersion,
                newContentJson,
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
                contentSchemaVersion,
                contentJson,
                newSortOrder,
                version + 1,
                createdBy,
                createdAt,
                now
        );
    }
}
