package com.devcollab.knowledgecore.auth.application;

import com.devcollab.knowledgecore.common.redis.RedisCoordinationProperties;
import com.devcollab.knowledgecore.common.redis.RedisFixedWindowLimiter;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginAttemptLimiterTests {

    @Test
    void identityIsNormalizedAndHashedBeforeEnteringRedisKey() {
        RedisFixedWindowLimiter redisLimiter = mock(RedisFixedWindowLimiter.class);
        RedisCoordinationProperties properties = new RedisCoordinationProperties(
                new RedisCoordinationProperties.LoginLimit(
                        5, Duration.ofMinutes(15)
                ),
                null
        );
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(
                redisLimiter, properties
        );
        when(redisLimiter.acquire(
                org.mockito.ArgumentMatchers.startsWith("login-limit:"),
                eq(5),
                eq(Duration.ofMinutes(15))
        )).thenReturn(new RedisFixedWindowLimiter.LimitDecision(
                true, 1, 900_000, true
        ));

        limiter.acquire(" Alice ");
        limiter.reset("alice");

        var keyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(redisLimiter).acquire(
                keyCaptor.capture(), eq(5), eq(Duration.ofMinutes(15))
        );
        assertThat(keyCaptor.getValue())
                .startsWith("login-limit:")
                .doesNotContain("alice");
        verify(redisLimiter).reset(keyCaptor.getValue());
    }
}
