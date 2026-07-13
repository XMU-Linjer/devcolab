package com.devcollab.knowledgecore.auth.api;

import com.devcollab.knowledgecore.security.CurrentUser;

import java.util.UUID;

public record CurrentUserResponse(
        UUID userId,
        String username
) {

    public static CurrentUserResponse from(CurrentUser currentUser) {
        return new CurrentUserResponse(
                currentUser.userId(),
                currentUser.username()
        );
    }
}