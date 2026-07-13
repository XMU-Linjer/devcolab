package com.devcollab.knowledgecore.document.api;

import com.devcollab.knowledgecore.document.domain.DocumentBlock;
import com.devcollab.knowledgecore.document.domain.DocumentBlockType;

import java.time.Instant;
import java.util.UUID;

public record DocumentBlockResponse(
        UUID id,
        UUID documentId,
        DocumentBlockType type,
        DocumentBlockContentResponse content,
        int sortOrder,
        long version,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {
    public static DocumentBlockResponse from(DocumentBlock block) {
        return new DocumentBlockResponse(
                block.id(),
                block.documentId(),
                block.type(),
                new DocumentBlockContentResponse(block.text()),
                block.sortOrder(),
                block.version(),
                block.createdBy(),
                block.createdAt(),
                block.updatedAt()
        );
    }
}
