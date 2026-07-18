package com.devcollab.knowledgecore.auth.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "devcollab.security.refresh")
public record RefreshTokenProperties(
        Duration ttl,
        String refreshCookieName,
        String csrfCookieName,
        String csrfHeaderName,
        List<String> allowedOrigins,
        boolean secure
) {

    public RefreshTokenProperties {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Refresh Token ttl 必须大于 0");
        }
        requireText(refreshCookieName, "Refresh Cookie 名称不能为空");
        requireText(csrfCookieName, "CSRF Cookie 名称不能为空");
        requireText(csrfHeaderName, "CSRF Header 名称不能为空");
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("允许的 Web Origin 不能为空");
        }
        allowedOrigins = allowedOrigins.stream()
                .map(RefreshTokenProperties::normalizeOrigin)
                .distinct()
                .toList();
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String normalizeOrigin(String value) {
        requireText(value, "允许的 Web Origin 不能为空");
        URI origin = URI.create(value.trim());
        if ((origin.getScheme() == null
                || !(origin.getScheme().equals("http")
                || origin.getScheme().equals("https")))
                || origin.getHost() == null
                || origin.getUserInfo() != null
                || origin.getQuery() != null
                || origin.getFragment() != null
                || (origin.getPath() != null
                && !origin.getPath().isEmpty()
                && !origin.getPath().equals("/"))) {
            throw new IllegalArgumentException(
                    "允许的 Web Origin 必须是 http(s) 协议的源地址"
            );
        }

        String normalized = origin.getScheme() + "://" + origin.getHost();
        if (origin.getPort() >= 0) {
            normalized += ":" + origin.getPort();
        }
        return normalized;
    }
}
