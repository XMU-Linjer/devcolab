package com.devcollab.knowledgecore.document.domain;

import java.time.Instant;
import java.util.UUID;

public record DocumentReviewRecord(
        UUID id,
        UUID documentId,
        DocumentReviewAction action,
        String comment,
        UUID operatorUserId,
        Instant createdAt
) {
}
