package com.devcollab.knowledgecore.agentdelegation;

import com.devcollab.knowledgecore.auth.infrastructure.JwtTokenService;
import com.devcollab.knowledgecore.git.domain.GitKnowledgeRepository;
import com.devcollab.knowledgecore.git.domain.GitProvider;
import com.devcollab.knowledgecore.git.domain.GitRepository;
import com.devcollab.knowledgecore.git.domain.GitRepositoryStatus;
import com.devcollab.knowledgecore.workspace.application.WorkspaceApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentDelegationServiceTests {

    private final AgentDelegationRepository repository = mock(AgentDelegationRepository.class);
    private final GitKnowledgeRepository gitRepository = mock(GitKnowledgeRepository.class);
    private final WorkspaceApplicationService workspaceService =
            mock(WorkspaceApplicationService.class);
    private final JwtTokenService tokenService = mock(JwtTokenService.class);
    private final AgentDelegationProperties properties = new AgentDelegationProperties(
            "test-agent-service-token-with-at-least-32-characters",
            Duration.ofHours(24),
            Duration.ofMinutes(15)
    );
    private AgentDelegationService service;

    @BeforeEach
    void setUp() {
        service = new AgentDelegationService(
                repository, gitRepository, workspaceService, tokenService, properties
        );
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsDelegationFromTrustedMembershipAndSynchronizedRevision() {
        UUID jobId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID repositoryId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(gitRepository.findRepositoryById(repositoryId))
                .thenReturn(Optional.of(repository(repositoryId, workspaceId, "abc123")));

        AgentDelegation delegation =
                service.create(jobId, workspaceId, repositoryId, userId);

        verify(workspaceService).requireMembership(workspaceId, userId);
        assertThat(delegation.jobId()).isEqualTo(jobId);
        assertThat(delegation.createdByUserId()).isEqualTo(userId);
        assertThat(delegation.revision()).isEqualTo("abc123");
        assertThat(delegation.allowedTools())
                .containsExactlyElementsOf(AgentDelegationService.CURRENT_FILE_TOOLS);
    }

    @Test
    void rejectsExchangeWhenServiceIdentityIsInvalid() {
        assertThatThrownBy(() ->
                service.exchange(UUID.randomUUID(), UUID.randomUUID(), "wrong"))
                .isInstanceOfSatisfying(AgentDelegationException.class, exception ->
                        assertThat(exception.code()).isEqualTo("AGENT_SERVICE_UNAUTHORIZED"));
    }

    @Test
    void rejectsExpiredDelegation() {
        AgentDelegation delegation = delegation(Instant.now().minusSeconds(1));
        when(repository.findById(delegation.id())).thenReturn(Optional.of(delegation));

        assertThatThrownBy(() ->
                service.exchange(
                        delegation.id(), delegation.jobId(), properties.serviceToken()
                ))
                .isInstanceOfSatisfying(AgentDelegationException.class, exception ->
                        assertThat(exception.code()).isEqualTo("AGENT_DELEGATION_EXPIRED"));
    }

    @Test
    void exchangesActiveDelegationForShortScopedToken() {
        AgentDelegation delegation = delegation(Instant.now().plusSeconds(600));
        when(repository.findById(delegation.id())).thenReturn(Optional.of(delegation));
        when(tokenService.issueAgentDelegationToken(
                any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn("short-scoped-token");

        String token = service.exchange(
                delegation.id(), delegation.jobId(), properties.serviceToken()
        );

        assertThat(token).isEqualTo("short-scoped-token");
        verify(workspaceService).requireMembership(
                delegation.workspaceId(), delegation.createdByUserId()
        );
        verify(tokenService).issueAgentDelegationToken(
                delegation.createdByUserId(),
                delegation.id(),
                delegation.workspaceId(),
                delegation.repositoryId(),
                delegation.jobId(),
                delegation.revision(),
                delegation.allowedTools(),
                properties.accessTokenTtl()
        );
    }

    private AgentDelegation delegation(Instant expiresAt) {
        return new AgentDelegation(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "abc123",
                AgentDelegationService.CURRENT_FILE_TOOLS,
                "ACTIVE",
                Instant.now().minusSeconds(60),
                expiresAt,
                null
        );
    }

    private GitRepository repository(UUID id, UUID workspaceId, String revision) {
        Instant now = Instant.now();
        return new GitRepository(
                id, workspaceId, "repo", GitProvider.GITHUB,
                "https://github.com/example/repo.git", "main", UUID.randomUUID(),
                now, now, GitRepositoryStatus.READY, revision, now, null
        );
    }
}
