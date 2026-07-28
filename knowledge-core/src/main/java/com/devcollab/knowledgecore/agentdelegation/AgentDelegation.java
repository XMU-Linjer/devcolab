package com.devcollab.knowledgecore.agentdelegation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AgentDelegation(
        UUID id,
        UUID jobId,
        UUID createdByUserId,
        UUID workspaceId,
        UUID repositoryId,
        String revision,
        List<String> allowedTools,
        String status,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt
) {
    public boolean activeAt(Instant now) {
        return "ACTIVE".equals(status) && revokedAt == null && expiresAt.isAfter(now);
    }
}
