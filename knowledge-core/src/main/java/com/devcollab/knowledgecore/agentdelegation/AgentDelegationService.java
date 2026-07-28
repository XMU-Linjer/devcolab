package com.devcollab.knowledgecore.agentdelegation;

import com.devcollab.knowledgecore.auth.infrastructure.JwtTokenService;
import com.devcollab.knowledgecore.git.application.exception.GitRepositoryNotFoundException;
import com.devcollab.knowledgecore.git.domain.GitKnowledgeRepository;
import com.devcollab.knowledgecore.git.domain.GitRepository;
import com.devcollab.knowledgecore.workspace.application.WorkspaceApplicationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AgentDelegationService {

    public static final List<String> CURRENT_FILE_TOOLS = List.of(
            "devcollab.workspace.get_context",
            "devcollab.code.read",
            "devcollab.binding.list",
            "devcollab.document.find_candidates",
            "devcollab.document.get_structure",
            "devcollab.review.submit_document_change"
    );

    private final AgentDelegationRepository repository;
    private final GitKnowledgeRepository gitRepository;
    private final WorkspaceApplicationService workspaceService;
    private final JwtTokenService tokenService;
    private final AgentDelegationProperties properties;

    public AgentDelegationService(
            AgentDelegationRepository repository,
            GitKnowledgeRepository gitRepository,
            WorkspaceApplicationService workspaceService,
            JwtTokenService tokenService,
            AgentDelegationProperties properties
    ) {
        this.repository = repository;
        this.gitRepository = gitRepository;
        this.workspaceService = workspaceService;
        this.tokenService = tokenService;
        this.properties = properties;
    }

    @Transactional
    public AgentDelegation create(
            UUID jobId,
            UUID workspaceId,
            UUID repositoryId,
            UUID currentUserId
    ) {
        workspaceService.requireMembership(workspaceId, currentUserId);
        GitRepository git = gitRepository.findRepositoryById(repositoryId)
                .filter(value -> value.workspaceId().equals(workspaceId))
                .orElseThrow(GitRepositoryNotFoundException::new);
        if (git.lastSyncedCommit() == null || git.lastSyncedCommit().isBlank()) {
            throw new AgentDelegationException(
                    HttpStatus.CONFLICT,
                    "REPOSITORY_NOT_SYNCED",
                    "Repository has no synchronized revision"
            );
        }
        Instant now = Instant.now();
        return repository.save(new AgentDelegation(
                UUID.randomUUID(), jobId, currentUserId, workspaceId, repositoryId,
                git.lastSyncedCommit(), CURRENT_FILE_TOOLS, "ACTIVE", now,
                now.plus(properties.delegationTtl()), null
        ));
    }

    public AgentDelegation authorize(
            UUID delegationId,
            UUID jobId,
            UUID currentUserId
    ) {
        AgentDelegation delegation = requireActive(delegationId, jobId);
        workspaceService.requireMembership(delegation.workspaceId(), currentUserId);
        if (!delegation.createdByUserId().equals(currentUserId)) {
            throw new AgentDelegationException(
                    HttpStatus.FORBIDDEN,
                    "AGENT_DELEGATION_DENIED",
                    "Agent job is not owned by the current user"
            );
        }
        return delegation;
    }

    public String exchange(UUID delegationId, UUID jobId, String serviceToken) {
        requireServiceToken(serviceToken);
        AgentDelegation delegation = requireActive(delegationId, jobId);
        workspaceService.requireMembership(
                delegation.workspaceId(), delegation.createdByUserId()
        );
        return tokenService.issueAgentDelegationToken(
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

    private AgentDelegation requireActive(UUID id, UUID jobId) {
        AgentDelegation delegation = repository.findById(id).orElseThrow(() ->
                new AgentDelegationException(
                        HttpStatus.NOT_FOUND,
                        "AGENT_DELEGATION_NOT_FOUND",
                        "Agent delegation was not found"
                )
        );
        if (!delegation.jobId().equals(jobId)) {
            throw new AgentDelegationException(
                    HttpStatus.FORBIDDEN,
                    "AGENT_DELEGATION_DENIED",
                    "Agent delegation does not belong to this job"
            );
        }
        if (!delegation.activeAt(Instant.now())) {
            throw new AgentDelegationException(
                    HttpStatus.FORBIDDEN,
                    "AGENT_DELEGATION_EXPIRED",
                    "Agent delegation is expired or revoked"
            );
        }
        return delegation;
    }

    private void requireServiceToken(String supplied) {
        byte[] expected = properties.serviceToken().getBytes(StandardCharsets.UTF_8);
        byte[] actual = supplied == null
                ? new byte[0] : supplied.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new AgentDelegationException(
                    HttpStatus.UNAUTHORIZED,
                    "AGENT_SERVICE_UNAUTHORIZED",
                    "Agent service authentication failed"
            );
        }
    }
}
