package com.devcollab.knowledgecore.auth.infrastructure;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RefreshTokenPropertiesTests {

    @Test
    void shouldNormalizeAndDeduplicateAllowedOrigins() {
        RefreshTokenProperties properties = properties(List.of(
                "http://localhost:5173/",
                "http://localhost:5173",
                "https://devcollab.example"
        ));

        assertEquals(
                List.of(
                        "http://localhost:5173",
                        "https://devcollab.example"
                ),
                properties.allowedOrigins()
        );
    }

    @Test
    void shouldRejectOriginContainingPath() {
        assertThrows(
                IllegalArgumentException.class,
                () -> properties(List.of(
                        "https://devcollab.example/application"
                ))
        );
    }

    @Test
    void shouldRejectWildcardOrigin() {
        assertThrows(
                IllegalArgumentException.class,
                () -> properties(List.of("*"))
        );
    }

    private RefreshTokenProperties properties(List<String> origins) {
        return new RefreshTokenProperties(
                Duration.ofDays(7),
                "dc_refresh",
                "dc_csrf",
                "X-CSRF-Token",
                origins,
                false
        );
    }
}
