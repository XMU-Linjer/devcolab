package com.devcollab.knowledgecore.auth.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshSessionRepository {

    RefreshSession save(RefreshSession session);

    Optional<RefreshSession> findActiveByTokenHash(
            String refreshTokenHash,
            Instant now
    );

    boolean consume(String refreshTokenHash, UUID sessionId);

    void revoke(String refreshTokenHash);
}
