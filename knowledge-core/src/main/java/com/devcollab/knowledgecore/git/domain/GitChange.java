package com.devcollab.knowledgecore.git.domain;

import java.time.Instant;
import java.util.UUID;

public record GitChange(
        UUID id,
        UUID repositoryId,
        GitChangeType changeType,
        String externalId,
        String title,
        String commitSha,
        String baseRef,
        String headRef,
        String authorName,
        String webUrl,
        Instant occurredAt,
        Instant createdAt
) {
}
