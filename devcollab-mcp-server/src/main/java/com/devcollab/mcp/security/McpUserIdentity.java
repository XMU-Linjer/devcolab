package com.devcollab.mcp.security;

import java.security.Principal;
import java.util.UUID;
import java.util.Set;

public record McpUserIdentity(
        UUID userId,
        UUID sessionId,
        String username,
        String accessToken,
        String tokenType,
        UUID delegationWorkspaceId,
        UUID delegationRepositoryId,
        UUID delegationJobId,
        String delegationRevision,
        Set<String> allowedTools
) implements Principal {

    public McpUserIdentity(
            UUID userId,
            UUID sessionId,
            String username,
            String accessToken
    ) {
        this(
                userId, sessionId, username, accessToken, "user",
                null, null, null, null, Set.of()
        );
    }

    public boolean delegated() {
        return "agent_delegation".equals(tokenType);
    }

    @Override
    public String getName() {
        return userId.toString();
    }
}
