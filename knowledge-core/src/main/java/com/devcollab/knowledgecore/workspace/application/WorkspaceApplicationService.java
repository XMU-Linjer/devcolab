package com.devcollab.knowledgecore.workspace.application;

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
import java.util.UUID;

@Service
public class WorkspaceApplicationService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;

    public WorkspaceApplicationService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository memberRepository
    ) {
        this.workspaceRepository = workspaceRepository;
        this.memberRepository = memberRepository;
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

    public WorkspaceMember requireMembership(
            UUID workspaceId,
            UUID currentUserId
    ) {
        requireWorkspace(workspaceId);
        return memberRepository
                .findByWorkspaceIdAndUserId(workspaceId, currentUserId)
                .orElseThrow(WorkspaceAccessDeniedException::new);
    }

    private Workspace requireWorkspace(UUID workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(WorkspaceNotFoundException::new);
    }
}
