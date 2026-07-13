package com.devcollab.knowledgecore.workspace.domain;

import java.util.Optional;
import java.util.UUID;

public interface WorkspaceMemberRepository {

    WorkspaceMember save(WorkspaceMember member);

    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(
            UUID workspaceId,
            UUID userId
    );
}
