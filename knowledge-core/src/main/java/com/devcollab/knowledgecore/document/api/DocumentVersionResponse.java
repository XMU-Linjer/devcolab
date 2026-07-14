package com.devcollab.knowledgecore.document.api;

import com.devcollab.knowledgecore.document.domain.DocumentVersion;

import java.time.Instant;
import java.util.UUID;

public record DocumentVersionResponse(
        UUID id,
        UUID documentId,
        int versionNo,
        String title,
        String snapshotPayload,
        UUID publishedBy,
        Instant publishedAt
) {
    public static DocumentVersionResponse from(DocumentVersion version) {
        return new DocumentVersionResponse(
                version.id(),
                version.documentId(),
                version.versionNo(),
                version.title(),
                version.snapshotPayload(),
                version.publishedBy(),
                version.publishedAt()
        );
    }
}
