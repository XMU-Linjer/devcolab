package com.devcollab.knowledgecore.document.domain;

import java.time.Instant;
import java.util.UUID;

public record ReviewIssue(
        UUID id,
        UUID documentVersionId,
        ReviewIssueType type,
        ReviewIssueSeverity severity,
        ReviewIssueStatus status,
        UUID assigneeId,
        String title,
        String description,
        UUID createdBy,
        Instant createdAt
) {
}
