package com.devcollab.gateway.collaboration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GatewayOperationDeduplicator {

    private static final Logger log =
            LoggerFactory.getLogger(GatewayOperationDeduplicator.class);

    private final StringRedisTemplate redisTemplate;
    private final GatewayProperties properties;
    private final ConcurrentHashMap<OperationKey, Boolean> localFallbackAccepted =
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
            Boolean inserted = redisTemplate.opsForValue().setIfAbsent(
                    redisKey(operationKey),
                    "1",
                    properties.operationDedupTtl()
            );
            if (inserted != null) {
                return inserted;
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
        localFallbackAccepted.remove(operationKey);
    }

    private boolean markFirstSeenLocally(OperationKey operationKey) {
        return localFallbackAccepted.putIfAbsent(operationKey, Boolean.TRUE) == null;
    }

    private String redisKey(OperationKey operationKey) {
        return "gateway:document:%s:operation:%s:%s".formatted(
                operationKey.documentId(),
                operationKey.userId(),
                operationKey.clientOperationId()
        );
    }

    private record OperationKey(
            UUID documentId,
            UUID userId,
            UUID clientOperationId
    ) {
    }
}
