package com.devcollab.knowledgecore.workspace.application;

import com.devcollab.knowledgecore.common.cache.CacheKey;
import com.devcollab.knowledgecore.common.cache.CacheProperties;
import com.devcollab.knowledgecore.common.cache.RedisCacheService;
import com.devcollab.knowledgecore.workspace.domain.WorkspaceMember;
import com.devcollab.knowledgecore.workspace.domain.WorkspaceMemberRepository;
import com.devcollab.knowledgecore.workspace.domain.WorkspaceRole;
import com.github.benmanes.caffeine.cache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class WorkspaceMemberCacheService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceMemberCacheService.class);

    private final Cache<String, CachedMembership> localCache;
    private final RedisCacheService redisCache;
    private final WorkspaceMemberRepository memberRepository;
    private final CacheProperties properties;

    public WorkspaceMemberCacheService(
            Cache<String, CachedMembership> localCache,
            RedisCacheService redisCache,
            WorkspaceMemberRepository memberRepository,
            CacheProperties properties
    ) {
        this.localCache = localCache;
        this.redisCache = redisCache;
        this.memberRepository = memberRepository;
        this.properties = properties;
    }

    public Optional<WorkspaceMember> findCached(UUID workspaceId, UUID userId) {
        String key = CacheKey.workspaceMember(workspaceId, userId);

        if (localCacheEnabled()) {
            CachedMembership local = localCache.getIfPresent(key);
            if (local != null) {
                return Optional.of(local.toWorkspaceMember());
            }
        }

        if (properties.enabled()) {
            Optional<CachedMembership> redisHit;
            try {
                redisHit = redisCache.get(key, CachedMembership.class);
            } catch (Exception e) {
                log.warn(
                        "Redis get failed for key={}, falling back to Repository: {}",
                        key,
                        e.getMessage()
                );
                redisHit = Optional.empty();
            }
            if (redisHit.isPresent()) {
                CachedMembership cached = redisHit.get();
                putLocal(key, cached);
                return Optional.of(cached.toWorkspaceMember());
            }
        }

        Optional<WorkspaceMember> member = memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId);
        if (member.isPresent()) {
            CachedMembership cached = CachedMembership.from(member.get());
            if (properties.enabled()) {
                redisCache.set(key, cached, properties.workspaceMemberTtl());
            }
            putLocal(key, cached);
        }
        return member;
    }

    public boolean existsCached(UUID workspaceId, UUID userId) {
        return findCached(workspaceId, userId).isPresent();
    }

    public void evict(UUID workspaceId, UUID userId) {
        String key = CacheKey.workspaceMember(workspaceId, userId);
        if (localCacheEnabled()) {
            localCache.invalidate(key);
        }
        if (properties.enabled()) {
            redisCache.evict(key);
        }
    }

    private void putLocal(String key, CachedMembership cached) {
        if (localCacheEnabled()) {
            localCache.put(key, cached);
        }
    }

    private boolean localCacheEnabled() {
        return properties.enabled() && properties.local().enabled();
    }

    public record CachedMembership(
            UUID workspaceId,
            UUID userId,
            WorkspaceRole role,
            Instant joinedAt
    ) {

        public static CachedMembership from(WorkspaceMember member) {
            return new CachedMembership(
                    member.workspaceId(),
                    member.userId(),
                    member.role(),
                    member.joinedAt()
            );
        }

        public WorkspaceMember toWorkspaceMember() {
            return new WorkspaceMember(workspaceId, userId, role, joinedAt);
        }
    }
}
