package com.devcollab.gateway.collaboration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GatewayOperationDeduplicator {

    private static final DefaultRedisScript<Long> MARK_FIRST_SEEN_SCRIPT =
            new DefaultRedisScript<>("""
                    if redis.call('EXISTS', KEYS[1]) == 1 then
                        return 0
                    end
                    redis.call('PSETEX', KEYS[1], ARGV[1], ARGV[2])
                    return 1
                    """, Long.class);

    private static final Logger log =
            LoggerFactory.getLogger(GatewayOperationDeduplicator.class);

    private final StringRedisTemplate redisTemplate;
    private final GatewayProperties properties;
    private final ConcurrentHashMap<UUID, Instant> localFallbackAccepted =
            new ConcurrentHashMap<>();

    public GatewayOperationDeduplicator(
            StringRedisTemplate redisTemplate,
            GatewayProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public boolean markFirstSeen(
            UUID documentId,
            UUID userId,
            UUID clientOperationId
    ) {
        OperationKey operationKey =
                new OperationKey(documentId, userId, clientOperationId);
        try {
            Long inserted = redisTemplate.execute(
                    MARK_FIRST_SEEN_SCRIPT,
                    java.util.List.of(redisKey(operationKey)),
                    Long.toString(properties.operationDedupTtl().toMillis()),
                    operationKey.documentId() + ":" + operationKey.userId()
            );
            if (inserted != null) {
                return inserted == 1L;
            }
            log.warn("Redis returned null while marking gateway operation, using local fallback");
        } catch (RuntimeException e) {
            log.warn(
                    "Redis unavailable while marking gateway operation, using local fallback: {}",
                    e.getMessage()
            );
            log.debug("Gateway operation dedup Redis failure detail", e);
        }
        return markFirstSeenLocally(operationKey);
    }

    public void forget(
            UUID documentId,
            UUID userId,
            UUID clientOperationId
    ) {
        OperationKey operationKey =
                new OperationKey(documentId, userId, clientOperationId);
        try {
            redisTemplate.delete(redisKey(operationKey));
        } catch (RuntimeException e) {
            log.warn(
                    "Redis unavailable while forgetting gateway operation, local fallback will be cleaned: {}",
                    e.getMessage()
            );
            log.debug("Gateway operation forget Redis failure detail", e);
        }
        localFallbackAccepted.remove(clientOperationId);
    }

    private boolean markFirstSeenLocally(OperationKey operationKey) {
        UUID operationId = operationKey.clientOperationId();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.operationDedupTtl());
        while (true) {
            Instant existing = localFallbackAccepted.get(operationId);
            if (existing == null) {
                if (localFallbackAccepted.putIfAbsent(
                        operationId, expiresAt
                ) == null) {
                    return true;
                }
                continue;
            }
            if (existing.isAfter(now)) {
                return false;
            }
            if (localFallbackAccepted.replace(
                    operationId, existing, expiresAt
            )) {
                return true;
            }
        }
    }

    private String redisKey(OperationKey operationKey) {
        return "dedup:" + operationKey.clientOperationId();
    }

    private record OperationKey(
            UUID documentId,
            UUID userId,
            UUID clientOperationId
    ) {
    }
}
