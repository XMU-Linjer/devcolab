package com.devcollab.knowledgecore.document.api;

import com.devcollab.knowledgecore.document.domain.Document;
import com.devcollab.knowledgecore.document.domain.DocumentReviewStatus;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        UUID workspaceId,
        UUID parentDocumentId,
        String title,
        DocumentReviewStatus reviewStatus,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {
    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.id(),
                document.workspaceId(),
                document.parentDocumentId(),
                document.title(),
                document.reviewStatus(),
                document.createdBy(),
                document.createdAt(),
                document.updatedAt()
        );
    }
}
