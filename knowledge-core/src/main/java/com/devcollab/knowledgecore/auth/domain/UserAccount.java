package com.devcollab.knowledgecore.auth.domain;

import java.time.Instant;
import java.util.UUID;

public record UserAccount(UUID id,
                          String username,
                          String normalizedUsername,
                          String displayName,
                          String passwordHash,
                          UserStatus status,
                          Instant createdAt,
                          Instant updatedAt
) {
    public boolean canLogin() {
        return status == UserStatus.ACTIVE;
    }
}
