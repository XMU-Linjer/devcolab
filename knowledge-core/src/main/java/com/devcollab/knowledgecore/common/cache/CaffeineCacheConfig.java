package com.devcollab.knowledgecore.common.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.devcollab.knowledgecore.document.application.DocumentTreeCacheService.DocumentList;
import com.devcollab.knowledgecore.workspace.application.WorkspaceMemberCacheService.CachedMembership;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Caffeine L1 (instance-local) cache configuration.
 *
 * <p>L1 TTL values are intentionally shorter than L2 (Redis) TTLs.
 * This means stale entries naturally expire at the instance level
 * while Redis serves as the shared source of cached data.
 * Multi-instance environments should additionally use Kafka-based
 * cache invalidation events; for single-instance MVP, TTL-based
 * expiry is sufficient.
 */
@Configuration
@EnableConfigurationProperties(CacheProperties.class)
public class CaffeineCacheConfig {

    private final CacheProperties properties;

    public CaffeineCacheConfig(CacheProperties properties) {
        this.properties = properties;
    }

    @Bean
    public Cache<String, CachedMembership> workspaceMemberLocalCache() {
        CacheProperties.Local local = properties.local();
        return Caffeine.newBuilder()
                .expireAfterWrite(local.workspaceMemberTtl())
                .maximumSize(local.workspaceMemberMaximumSize())
                .recordStats()
                .build();
    }

    @Bean
    public Cache<String, DocumentList> documentTreeLocalCache() {
        CacheProperties.Local local = properties.local();
        return Caffeine.newBuilder()
                .expireAfterWrite(local.documentTreeTtl())
                .maximumSize(local.documentTreeMaximumSize())
                .recordStats()
                .build();
    }
}