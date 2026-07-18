package com.devcollab.knowledgecore.auth.infrastructure;

import com.devcollab.knowledgecore.auth.application.exception.InvalidCsrfTokenException;
import com.devcollab.knowledgecore.auth.application.exception.InvalidRefreshTokenException;
import com.devcollab.knowledgecore.auth.infrastructure.RefreshTokenService.RefreshCredentials;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RefreshTokenServiceTests {

    @Test
    void shouldRotateRefreshTokenAndRejectOldToken() {
        RefreshTokenService service = refreshTokenService();
        UUID userId = UUID.randomUUID();
        RefreshCredentials original = service.issue(userId);

        RefreshCredentials rotated = service.rotate(
                original.refreshToken(),
                original.csrfToken()
        );

        assertEquals(userId, rotated.userId());
        assertNotEquals(original.sessionId(), rotated.sessionId());
        assertNotEquals(original.refreshToken(), rotated.refreshToken());
        assertNotEquals(original.csrfToken(), rotated.csrfToken());
        assertThrows(
                InvalidRefreshTokenException.class,
                () -> service.rotate(
                        original.refreshToken(),
                        original.csrfToken()
                )
        );
    }

    @Test
    void shouldRejectWrongCsrfTokenWithoutConsumingSession() {
        RefreshTokenService service = refreshTokenService();
        RefreshCredentials credentials = service.issue(UUID.randomUUID());

        assertThrows(
                InvalidCsrfTokenException.class,
                () -> service.rotate(
                        credentials.refreshToken(),
                        "wrong-csrf-token"
                )
        );

        service.rotate(
                credentials.refreshToken(),
                credentials.csrfToken()
        );
    }

    private RefreshTokenService refreshTokenService() {
        RefreshTokenProperties properties = new RefreshTokenProperties(
                Duration.ofDays(7),
                "dc_refresh",
                "dc_csrf",
                "X-CSRF-Token",
                List.of("http://localhost:5173"),
                false
        );

        return new RefreshTokenService(
                new InMemoryRefreshSessionRepository(),
                properties
        );
    }
}
