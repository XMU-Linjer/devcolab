package com.devcollab.gateway.collaboration;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GatewayOperationDeduplicatorTests {

    @Test
    void firstOperationIsAcceptedAndDuplicateIsRejected() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), anyList(), anyString(), anyString()))
                .thenReturn(1L)
                .thenReturn(0L);
        GatewayOperationDeduplicator deduplicator = deduplicator(redis);
        UUID operationId = UUID.randomUUID();

        assertThat(deduplicator.markFirstSeen(
                UUID.randomUUID(), UUID.randomUUID(), operationId
        )).isTrue();
        assertThat(deduplicator.markFirstSeen(
                UUID.randomUUID(), UUID.randomUUID(), operationId
        )).isFalse();
    }

    @Test
    void operationIdIsAClusterWideIdempotencyKey() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), anyList(), anyString(), anyString()))
                .thenReturn(1L)
                .thenReturn(0L);
        GatewayOperationDeduplicator deduplicator = deduplicator(redis);
        UUID operationId = UUID.randomUUID();

        assertThat(deduplicator.markFirstSeen(
                UUID.randomUUID(), UUID.randomUUID(), operationId
        )).isTrue();
        assertThat(deduplicator.markFirstSeen(
                UUID.randomUUID(), UUID.randomUUID(), operationId
        )).isFalse();
    }

    @Test
    void forgottenOperationCanBeRetried() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), anyList(), anyString(), anyString()))
                .thenReturn(1L);
        GatewayOperationDeduplicator deduplicator = deduplicator(redis);
        UUID documentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();

        assertThat(deduplicator.markFirstSeen(documentId, userId, operationId))
                .isTrue();
        deduplicator.forget(documentId, userId, operationId);

        verify(redis).delete("dedup:" + operationId);
        assertThat(deduplicator.markFirstSeen(documentId, userId, operationId))
                .isTrue();
    }

    @Test
    void redisFailureFallsBackToLocalDeduplication() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), anyList(), anyString(), anyString()))
                .thenThrow(new RedisConnectionFailureException("redis down"));
        GatewayOperationDeduplicator deduplicator = deduplicator(redis);
        UUID documentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();

        assertThat(deduplicator.markFirstSeen(documentId, userId, operationId))
                .isTrue();
        assertThat(deduplicator.markFirstSeen(
                UUID.randomUUID(), UUID.randomUUID(), operationId
        ))
                .isFalse();
    }

    @Test
    void localFallbackEntryExpires() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), anyList(), anyString(), anyString()))
                .thenReturn(null);
        GatewayOperationDeduplicator deduplicator = new GatewayOperationDeduplicator(
                redis,
                new GatewayProperties(
                        "http://core.example",
                        Duration.ofMinutes(1),
                        Duration.ofMinutes(1),
                        Duration.ofMillis(1)
                )
        );
        UUID operationId = UUID.randomUUID();

        assertThat(deduplicator.markFirstSeen(
                UUID.randomUUID(), UUID.randomUUID(), operationId
        )).isTrue();
        Thread.sleep(10);
        assertThat(deduplicator.markFirstSeen(
                UUID.randomUUID(), UUID.randomUUID(), operationId
        )).isTrue();
    }

    @Test
    void redisNullResultFallsBackToLocalDeduplication() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), anyList(), anyString(), anyString()))
                .thenReturn(null);
        GatewayOperationDeduplicator deduplicator = deduplicator(redis);
        UUID documentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();

        assertThat(deduplicator.markFirstSeen(documentId, userId, operationId))
                .isTrue();
        assertThat(deduplicator.markFirstSeen(documentId, userId, operationId))
                .isFalse();
    }

    private GatewayOperationDeduplicator deduplicator(
            StringRedisTemplate redis
    ) {
        return new GatewayOperationDeduplicator(
                redis,
                new GatewayProperties(
                        "http://core.example",
                        Duration.ofMinutes(1),
                        Duration.ofMinutes(1),
                        Duration.ofMinutes(5)
                )
        );
    }
}
