package com.devcollab.knowledgecore.common.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Minimal Redis cache facade.
 *
 * <p>All Redis errors are logged and swallowed — cache is a performance
 * optimisation, never the authoritative data source.
 */
@Service
public class RedisCacheService {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheService.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisCacheService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            String json = redis.opsForValue().get(key);
            if (json == null || json.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, type));
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize cache key={}: {}", key, e.getMessage());
            evict(key);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Redis get failed for key={}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    public <T> void set(String key, T value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redis.opsForValue().set(key, json, ttl);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize value for cache key={}: {}", key, e.getMessage());
        } catch (Exception e) {
            log.warn("Redis set failed for key={}: {}", key, e.getMessage());
        }
    }

    public void evict(String key) {
        try {
            redis.delete(key);
        } catch (Exception e) {
            log.warn("Redis evict failed for key={}: {}", key, e.getMessage());
        }
    }
}