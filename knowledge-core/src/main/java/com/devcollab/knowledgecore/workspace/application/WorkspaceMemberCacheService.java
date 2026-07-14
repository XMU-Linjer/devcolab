package com.devcollab.knowledgecore.workspace.application;

import com.devcollab.knowledgecore.common.cache.CacheKey;
import com.devcollab.knowledgecore.common.cache.CacheProperties;
import com.devcollab.knowledgecore.common.cache.RedisCacheService;
import com.devcollab.knowledgecore.workspace.domain.WorkspaceMember;
import com.devcollab.knowledgecore.workspace.domain.WorkspaceMemberRepository;
import com.devcollab.knowledgecore.workspace.domain.WorkspaceRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class WorkspaceMemberCacheService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceMemberCacheService.class);

    private final RedisCacheService cache;
    private final WorkspaceMemberRepository memberRepository;
    private final CacheProperties properties;

    public WorkspaceMemberCacheService(
            RedisCacheService cache,
            WorkspaceMemberRepository memberRepository,
            CacheProperties properties
    ) {
        this.cache = cache;
        this.memberRepository = memberRepository;
        this.properties = properties;
    }

    public Optional<WorkspaceMember> findCached(UUID workspaceId, UUID userId) {
        String key = CacheKey.workspaceMember(workspaceId, userId);
        Optional<CachedMembership> cached = cache.get(key, CachedMembership.class);
        if (cached.isPresent()) {
            return Optional.of(cached.get().toWorkspaceMember());
        }
        Optional<WorkspaceMember> member = memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId);
        member.ifPresent(value -> cache.set(key, CachedMembership.from(value), properties.workspaceMemberTtl()));
        return member;
    }

    public boolean existsCached(UUID workspaceId, UUID userId) {
        return findCached(workspaceId, userId).isPresent();
    }

    public void evict(UUID workspaceId, UUID userId) {
        cache.evict(CacheKey.workspaceMember(workspaceId, userId));
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