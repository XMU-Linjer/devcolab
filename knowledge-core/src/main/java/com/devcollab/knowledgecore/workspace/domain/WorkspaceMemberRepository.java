package com.devcollab.knowledgecore.workspace.domain;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface WorkspaceMemberRepository {

    WorkspaceMember save(WorkspaceMember member);

    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(
            UUID workspaceId,
            UUID userId
    );

    List<WorkspaceMember> findAllByWorkspaceId(UUID workspaceId);

    boolean existsByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    long countByWorkspaceIdAndRole(UUID workspaceId, WorkspaceRole role);

    void deleteByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);
}
