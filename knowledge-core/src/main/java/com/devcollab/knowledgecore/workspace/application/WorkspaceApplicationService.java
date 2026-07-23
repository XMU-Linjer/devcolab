package com.devcollab.knowledgecore.workspace.application;

import com.devcollab.knowledgecore.common.outbox.application.OutboxEventPublisher;
import com.devcollab.knowledgecore.common.outbox.application.OutboxEventTypes;
import com.devcollab.knowledgecore.git.domain.GitKnowledgeRepository;
import com.devcollab.knowledgecore.git.domain.GitRepository;
import com.devcollab.knowledgecore.workspace.application.exception.WorkspaceAccessDeniedException;
import com.devcollab.knowledgecore.workspace.application.exception.WorkspaceNotFoundException;
import com.devcollab.knowledgecore.workspace.domain.Workspace;
import com.devcollab.knowledgecore.workspace.domain.WorkspaceMember;
import com.devcollab.knowledgecore.workspace.domain.WorkspaceMemberRepository;
import com.devcollab.knowledgecore.workspace.domain.WorkspaceRepository;
import com.devcollab.knowledgecore.workspace.domain.WorkspaceRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WorkspaceApplicationService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final WorkspaceMemberCacheService memberCache;
    private final GitKnowledgeRepository gitRepository;
    private final OutboxEventPublisher outboxPublisher;

    public WorkspaceApplicationService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository memberRepository,
            WorkspaceMemberCacheService memberCache,
            GitKnowledgeRepository gitRepository,
            OutboxEventPublisher outboxPublisher
    ) {
        this.workspaceRepository = workspaceRepository;
        this.memberRepository = memberRepository;
        this.memberCache = memberCache;
        this.gitRepository = gitRepository;
        this.outboxPublisher = outboxPublisher;
    }

    @Transactional
    public WorkspaceView create(
            UUID currentUserId,
            CreateWorkspaceCommand command
    ) {
        Instant now = Instant.now();
        Workspace workspace = new Workspace(
                UUID.randomUUID(),
                command.name().trim(),
                currentUserId,
                now,
                now
        );
        WorkspaceMember owner = new WorkspaceMember(
                workspace.id(),
                currentUserId,
                WorkspaceRole.ADMIN,
                now
        );

        workspaceRepository.save(workspace);
        memberRepository.save(owner);

        return WorkspaceView.from(workspace, owner.role());
    }

    public List<WorkspaceView> listForUser(UUID currentUserId) {
        return workspaceRepository.findAllByUserId(currentUserId).stream()
                .map(workspace -> WorkspaceView.from(
                        workspace,
                        requireMembership(workspace.id(), currentUserId).role()
                ))
                .toList();
    }

    public WorkspaceView get(UUID workspaceId, UUID currentUserId) {
        Workspace workspace = requireWorkspace(workspaceId);
        WorkspaceMember member = requireMembership(
                workspaceId,
                currentUserId
        );
        return WorkspaceView.from(workspace, member.role());
    }

    @Transactional
    public void delete(UUID workspaceId, UUID currentUserId) {
        requireWorkspace(workspaceId);
        WorkspaceMember currentMember = memberRepository
                .findByWorkspaceIdAndUserId(workspaceId, currentUserId)
                .orElseThrow(WorkspaceNotFoundException::new);
        if (currentMember.role() != WorkspaceRole.ADMIN) {
            throw new WorkspaceAccessDeniedException();
        }

        List<WorkspaceMember> members =
                memberRepository.findAllByWorkspaceId(workspaceId);
        List<GitRepository> repositories =
                gitRepository.findRepositoriesByWorkspaceId(workspaceId);

        workspaceRepository.deleteById(workspaceId);
        for (GitRepository repository : repositories) {
            outboxPublisher.publish(
                    "GIT_REPOSITORY",
                    repository.id(),
                    OutboxEventTypes.GIT_REPOSITORY_DELETE_REQUESTED,
                    Map.of(
                            "workspaceId", workspaceId.toString(),
                            "repositoryId", repository.id().toString()
                    )
            );
        }
        members.forEach(member ->
                memberCache.evict(workspaceId, member.userId())
        );
    }

    public WorkspaceMember requireMembership(
            UUID workspaceId,
            UUID currentUserId
    ) {
        requireWorkspace(workspaceId);
        return memberCache
                .findCached(workspaceId, currentUserId)
                .orElseThrow(WorkspaceAccessDeniedException::new);
    }

    private Workspace requireWorkspace(UUID workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(WorkspaceNotFoundException::new);
    }
}
