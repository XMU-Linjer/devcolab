package com.devcollab.knowledgecore.auth.api;

import com.devcollab.knowledgecore.auth.application.AuthenticatedUser;

import java.util.UUID;

public record AuthResponse(
        UUID userId,
        String username,
        String displayName,
        String accessToken,
        String tokenType,
        long expiresInSeconds
) {
    public static AuthResponse from(AuthenticatedUser authenticatedUser) {
        return new AuthResponse(
                authenticatedUser.id(),
                authenticatedUser.username(),
                authenticatedUser.displayName(),
                authenticatedUser.accessToken(),
                authenticatedUser.tokenType(),
                authenticatedUser.expiresInSeconds()
        );
    }
}