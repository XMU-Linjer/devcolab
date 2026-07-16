package com.devcollab.gateway.collaboration;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GatewayOperationDeduplicatorTests {

    @Test
    void firstOperationIsAcceptedAndDuplicateIsRejected() {
        RedisFixture fixture = redisFixture();
        UUID documentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        when(fixture.valueOperations().setIfAbsent(
                expectedKey(documentId, userId, operationId),
                "1",
                Duration.ofMinutes(5)
        ))
                .thenReturn(true)
                .thenReturn(false);

        GatewayOperationDeduplicator deduplicator =
                new GatewayOperationDeduplicator(
                        fixture.redisTemplate(),
                        properties()
                );

        assertThat(deduplicator.markFirstSeen(
                documentId,
                userId,
                operationId
        )).isTrue();

        assertThat(deduplicator.markFirstSeen(
                documentId,
                userId,
                operationId
        )).isFalse();
    }

    @Test
    void sameOperationIdInDifferentDocumentIsIndependent() {
        RedisFixture fixture = redisFixture();
        UUID userId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        when(fixture.valueOperations().setIfAbsent(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("1"),
                org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(5))
        )).thenReturn(true);

        GatewayOperationDeduplicator deduplicator =
                new GatewayOperationDeduplicator(
                        fixture.redisTemplate(),
                        properties()
                );

        assertThat(deduplicator.markFirstSeen(
                UUID.randomUUID(),
                userId,
                operationId
        )).isTrue();

        assertThat(deduplicator.markFirstSeen(
                UUID.randomUUID(),
                userId,
                operationId
        )).isTrue();
    }

    @Test
    void forgottenOperationCanBeRetried() {
        RedisFixture fixture = redisFixture();
        UUID documentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        String expectedKey = expectedKey(documentId, userId, operationId);
        when(fixture.valueOperations().setIfAbsent(
                expectedKey,
                "1",
                Duration.ofMinutes(5)
        ))
                .thenReturn(true)
                .thenReturn(true);

        GatewayOperationDeduplicator deduplicator =
                new GatewayOperationDeduplicator(
                        fixture.redisTemplate(),
                        properties()
                );

        assertThat(deduplicator.markFirstSeen(
                documentId,
                userId,
                operationId
        )).isTrue();

        deduplicator.forget(documentId, userId, operationId);

        verify(fixture.redisTemplate()).delete(expectedKey);

        assertThat(deduplicator.markFirstSeen(
                documentId,
                userId,
                operationId
        )).isTrue();
    }

    @Test
    void redisFailureFallsBackToLocalDeduplication() {
        RedisFixture fixture = redisFixture();
        UUID documentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        when(fixture.valueOperations().setIfAbsent(
                expectedKey(documentId, userId, operationId),
                "1",
                Duration.ofMinutes(5)
        )).thenThrow(new RedisConnectionFailureException("redis down"));

        GatewayOperationDeduplicator deduplicator =
                new GatewayOperationDeduplicator(
                        fixture.redisTemplate(),
                        properties()
                );

        assertThat(deduplicator.markFirstSeen(
                documentId,
                userId,
                operationId
        )).isTrue();

        assertThat(deduplicator.markFirstSeen(
                documentId,
                userId,
                operationId
        )).isFalse();
    }

    @Test
    void redisNullResultFallsBackToLocalDeduplication() {
        RedisFixture fixture = redisFixture();
        UUID documentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        when(fixture.valueOperations().setIfAbsent(
                expectedKey(documentId, userId, operationId),
                "1",
                Duration.ofMinutes(5)
        )).thenReturn(null);

        GatewayOperationDeduplicator deduplicator =
                new GatewayOperationDeduplicator(
                        fixture.redisTemplate(),
                        properties()
                );

        assertThat(deduplicator.markFirstSeen(
                documentId,
                userId,
                operationId
        )).isTrue();

        assertThat(deduplicator.markFirstSeen(
                documentId,
                userId,
                operationId
        )).isFalse();
    }

    private RedisFixture redisFixture() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations =
                mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        return new RedisFixture(redisTemplate, valueOperations);
    }

    private GatewayProperties properties() {
        return new GatewayProperties(
                "http://core.example",
                Duration.ofMinutes(1),
                Duration.ofMinutes(1),
                Duration.ofMinutes(5)
        );
    }

    private String expectedKey(
            UUID documentId,
            UUID userId,
            UUID operationId
    ) {
        return "gateway:document:%s:operation:%s:%s".formatted(
                documentId,
                userId,
                operationId
        );
    }

    private record RedisFixture(
            StringRedisTemplate redisTemplate,
            ValueOperations<String, String> valueOperations
    ) {
    }
}
