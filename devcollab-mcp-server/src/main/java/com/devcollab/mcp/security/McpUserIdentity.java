package com.devcollab.mcp.security;

import java.security.Principal;
import java.util.UUID;

public record McpUserIdentity(
        UUID userId,
        UUID sessionId,
        String username,
        String accessToken
) implements Principal {

    @Override
    public String getName() {
        return userId.toString();
    }
}
