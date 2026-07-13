package com.devcollab.knowledgecore.auth.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "devcollab.security.refresh")
public record RefreshTokenProperties(
        Duration ttl,
        String refreshCookieName,
        String csrfCookieName,
        String csrfHeaderName,
        String allowedOrigin,
        boolean secure
) {

    public RefreshTokenProperties {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Refresh Token ttl 必须大于 0");
        }
        requireText(refreshCookieName, "Refresh Cookie 名称不能为空");
        requireText(csrfCookieName, "CSRF Cookie 名称不能为空");
        requireText(csrfHeaderName, "CSRF Header 名称不能为空");
        requireText(allowedOrigin, "允许的 Web Origin 不能为空");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
