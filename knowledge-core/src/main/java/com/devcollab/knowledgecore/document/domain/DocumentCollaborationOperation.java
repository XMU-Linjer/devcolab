package com.devcollab.knowledgecore.document.domain;

import java.time.Instant;
import java.util.UUID;

public record DocumentCollaborationOperation(
        UUID id,
        UUID documentId,
        long documentSequence,
        UUID clientOperationId,
        String operationType,
        UUID operatorUserId,
        String requestFingerprint,
        DocumentBlock block,
        Instant createdAt
) {
}
