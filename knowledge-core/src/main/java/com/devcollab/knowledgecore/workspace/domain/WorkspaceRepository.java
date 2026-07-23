package com.devcollab.knowledgecore.workspace.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceRepository {

    Workspace save(Workspace workspace);

    Optional<Workspace> findById(UUID workspaceId);

    List<Workspace> findAllByUserId(UUID userId);

    void deleteById(UUID workspaceId);
}
