package com.devcollab.knowledgecore.search.projection;

import com.devcollab.knowledgecore.search.domain.SearchHitType;

import java.time.Instant;
import java.util.UUID;

public record SearchIndexEntry(
        String id,
        UUID workspaceId,
        UUID documentId,
        String documentTitle,
        SearchHitType hitType,
        UUID blockId,
        String text,
        int sortOrder,
        Instant updatedAt
) {
    public static SearchIndexEntry documentTitle(
            UUID workspaceId,
            UUID documentId,
            String documentTitle,
            Instant updatedAt
    ) {
        return new SearchIndexEntry(
                documentIndexId(documentId),
                workspaceId,
                documentId,
                documentTitle,
                SearchHitType.DOCUMENT_TITLE,
                null,
                documentTitle,
                0,
                updatedAt
        );
    }

    public static SearchIndexEntry blockContent(
            UUID workspaceId,
            UUID documentId,
            String documentTitle,
            UUID blockId,
            String text,
            int sortOrder,
            Instant updatedAt
    ) {
        return new SearchIndexEntry(
                blockIndexId(blockId),
                workspaceId,
                documentId,
                documentTitle,
                SearchHitType.BLOCK_CONTENT,
                blockId,
                text,
                sortOrder,
                updatedAt
        );
    }

    public static String documentIndexId(UUID documentId) {
        return "document-" + documentId;
    }

    public static String blockIndexId(UUID blockId) {
        return "block-" + blockId;
    }
}
