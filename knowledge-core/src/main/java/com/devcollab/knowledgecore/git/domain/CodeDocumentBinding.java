package com.devcollab.knowledgecore.git.domain;

import java.time.Instant;
import java.util.UUID;

public record CodeDocumentBinding(
        UUID id,
        UUID workspaceId,
        UUID repositoryId,
        UUID documentId,
        UUID blockId,
        String targetKey,
        String pathPattern,
        String revision,
        CodeAnchorKind anchorKind,
        String symbolKey,
        Integer startLine,
        Integer endLine,
        UUID createdBy,
        Instant createdAt
) {
    public CodeDocumentBinding(
            UUID id,
            UUID workspaceId,
            UUID repositoryId,
            UUID documentId,
            UUID blockId,
            String pathPattern,
            UUID createdBy,
            Instant createdAt
    ) {
        this(
                id,
                workspaceId,
                repositoryId,
                documentId,
                blockId,
                blockId == null ? "DOCUMENT" : blockId.toString(),
                pathPattern,
                null,
                CodeAnchorKind.FILE,
                null,
                null,
                null,
                createdBy,
                createdAt
        );
    }
}
