package com.devcollab.knowledgecore.auth.infrastructure;

import com.devcollab.knowledgecore.auth.application.exception.InvalidCsrfTokenException;
import com.devcollab.knowledgecore.auth.application.exception.InvalidRefreshTokenException;
import com.devcollab.knowledgecore.auth.domain.RefreshSession;
import com.devcollab.knowledgecore.auth.domain.RefreshSessionRepository;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Component
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshSessionRepository sessionRepository;
    private final RefreshTokenProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            RefreshSessionRepository sessionRepository,
            RefreshTokenProperties properties
    ) {
        this.sessionRepository = sessionRepository;
        this.properties = properties;
    }

    public RefreshCredentials issue(UUID userId) {
        Instant now = Instant.now();
        String refreshToken = randomToken();
        String csrfToken = randomToken();

        RefreshSession session = new RefreshSession(
                UUID.randomUUID(),
                userId,
                hash(refreshToken),
                hash(csrfToken),
                now,
                now.plus(properties.ttl())
        );

        sessionRepository.save(session);

        return new RefreshCredentials(
                session.id(),
                session.userId(),
                refreshToken,
                csrfToken
        );
    }

    public RefreshCredentials rotate(
            String refreshToken,
            String csrfToken
    ) {
        String refreshTokenHash = hash(refreshToken);
        RefreshSession session = sessionRepository
                .findActiveByTokenHash(refreshTokenHash, Instant.now())
                .orElseThrow(InvalidRefreshTokenException::new);

        if (!secureEquals(session.csrfTokenHash(), hash(csrfToken))) {
            throw new InvalidCsrfTokenException();
        }

        boolean consumed = sessionRepository.consume(
                refreshTokenHash,
                session.id()
        );

        if (!consumed) {
            throw new InvalidRefreshTokenException();
        }

        return issue(session.userId());
    }

    public void revoke(String refreshToken) {
        sessionRepository.revoke(hash(refreshToken));
    }

    public void revoke(
            String refreshToken,
            String csrfToken
    ) {
        String refreshTokenHash = hash(refreshToken);
        sessionRepository
                .findActiveByTokenHash(refreshTokenHash, Instant.now())
                .ifPresent(session -> {
                    if (!secureEquals(
                            session.csrfTokenHash(),
                            hash(csrfToken)
                    )) {
                        throw new InvalidCsrfTokenException();
                    }

                    sessionRepository.consume(
                            refreshTokenHash,
                            session.id()
                    );
                });
    }

    private String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 Java 环境不支持 SHA-256", exception);
        }
    }

    private boolean secureEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8)
        );
    }

    public record RefreshCredentials(
            UUID sessionId,
            UUID userId,
            String refreshToken,
            String csrfToken
    ) {
    }
}
