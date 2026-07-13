package com.devcollab.knowledgecore.security;

import java.util.UUID;

public record CurrentUser(
        UUID userId,
        String username
) {
}