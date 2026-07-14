package com.devcollab.knowledgecore.workspace.application;

import com.devcollab.knowledgecore.common.cache.CacheKey;
import com.devcollab.knowledgecore.common.cache.CacheProperties;
import com.devcollab.knowledgecore.common.cache.RedisCacheService;
import com.devcollab.knowledgecore.workspace.application.WorkspaceMemberCacheService.CachedMembership;
import com.devcollab.knowledgecore.workspace.domain.WorkspaceMember;
import com.devcollab.knowledgecore.workspace.domain.WorkspaceMemberRepository;
import com.devcollab.knowledgecore.workspace.domain.WorkspaceRole;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkspaceMemberCacheServiceTests {

    private final RedisCacheService cache = mock(RedisCacheService.class);
    private final WorkspaceMemberRepository repository = mock(WorkspaceMemberRepository.class);
    private final CacheProperties properties = new CacheProperties(true, Duration.ofMinutes(10), Duration.ofMinutes(5));
    private final WorkspaceMemberCacheService service = new WorkspaceMemberCacheService(
            cache,
            repository,
            properties
    );

    @Test
    void shouldReturnCachedMembershipWithoutQueryingRepository() {
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String key = CacheKey.workspaceMember(workspaceId, userId);
        Instant joinedAt = Instant.parse("2026-07-14T00:00:00Z");
        when(cache.get(key, CachedMembership.class))
                .thenReturn(Optional.of(new CachedMembership(
                        workspaceId,
                        userId,
                        WorkspaceRole.ADMIN,
                        joinedAt
                )));

        Optional<WorkspaceMember> member = service.findCached(workspaceId, userId);

        assertThat(member).contains(new WorkspaceMember(
                workspaceId,
                userId,
                WorkspaceRole.ADMIN,
                joinedAt
        ));
        verify(repository, never()).findByWorkspaceIdAndUserId(workspaceId, userId);
    }

    @Test
    void shouldLoadFromRepositoryAndWriteRedisOnCacheMiss() {
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String key = CacheKey.workspaceMember(workspaceId, userId);
        WorkspaceMember member = new WorkspaceMember(
                workspaceId,
                userId,
                WorkspaceRole.MEMBER,
                Instant.parse("2026-07-14T00:00:00Z")
        );
        when(cache.get(key, CachedMembership.class)).thenReturn(Optional.empty());
        when(repository.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(Optional.of(member));

        Optional<WorkspaceMember> result = service.findCached(workspaceId, userId);

        assertThat(result).contains(member);
        verify(cache).set(eq(key), eq(CachedMembership.from(member)), eq(properties.workspaceMemberTtl()));
    }

    @Test
    void shouldEvictMembershipKey() {
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        service.evict(workspaceId, userId);

        verify(cache).evict(CacheKey.workspaceMember(workspaceId, userId));
    }
}
