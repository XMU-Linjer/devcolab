package com.devcollab.knowledgecore.auth.infrastructure;

import com.devcollab.knowledgecore.auth.domain.RefreshSession;
import com.devcollab.knowledgecore.auth.domain.RefreshSessionRepository;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("in-memory")
public class InMemoryRefreshSessionRepository
        implements RefreshSessionRepository {

    private final Map<String, RefreshSession> sessionsByTokenHash =
            new HashMap<>();

    @Override
    public synchronized RefreshSession save(RefreshSession session) {
        sessionsByTokenHash.put(session.refreshTokenHash(), session);
        return session;
    }

    @Override
    public synchronized Optional<RefreshSession> findActiveByTokenHash(
            String refreshTokenHash,
            Instant now
    ) {
        RefreshSession session = sessionsByTokenHash.get(refreshTokenHash);

        if (session == null) {
            return Optional.empty();
        }

        if (session.isExpired(now)) {
            sessionsByTokenHash.remove(refreshTokenHash);
            return Optional.empty();
        }

        return Optional.of(session);
    }

    @Override
    public synchronized boolean consume(
            String refreshTokenHash,
            UUID sessionId
    ) {
        RefreshSession session = sessionsByTokenHash.get(refreshTokenHash);

        if (session == null || !session.id().equals(sessionId)) {
            return false;
        }

        sessionsByTokenHash.remove(refreshTokenHash);
        return true;
    }

    @Override
    public synchronized void revoke(String refreshTokenHash) {
        sessionsByTokenHash.remove(refreshTokenHash);
    }
}
