package com.devcollab.knowledgecore.auth.application;

import com.devcollab.knowledgecore.common.redis.RedisCoordinationProperties;
import com.devcollab.knowledgecore.common.redis.RedisFixedWindowLimiter;
import com.devcollab.knowledgecore.common.redis.RedisFixedWindowLimiter.LimitDecision;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class LoginAttemptLimiter {

    private final RedisFixedWindowLimiter limiter;
    private final RedisCoordinationProperties properties;

    public LoginAttemptLimiter(
            RedisFixedWindowLimiter limiter,
            RedisCoordinationProperties properties
    ) {
        this.limiter = limiter;
        this.properties = properties;
    }

    public LimitDecision acquire(String identity) {
        RedisCoordinationProperties.LoginLimit policy = properties.loginLimit();
        return limiter.acquire(
                key(identity),
                policy.maxAttempts(),
                policy.window()
        );
    }

    public void reset(String identity) {
        limiter.reset(key(identity));
    }

    private String key(String identity) {
        String normalized = identity == null
                ? ""
                : identity.trim().toLowerCase(Locale.ROOT);
        return "login-limit:" + sha256(normalized);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
