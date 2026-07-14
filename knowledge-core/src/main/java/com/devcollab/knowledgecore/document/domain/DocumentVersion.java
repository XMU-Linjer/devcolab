package com.devcollab.knowledgecore.document.domain;

import java.time.Instant;
import java.util.UUID;

public record DocumentVersion(
        UUID id,
        UUID documentId,
        int versionNo,
        String title,
        DocumentVersionStatus status,
        String snapshotPayload,
        UUID publishedBy,
        Instant publishedAt
) {
}
