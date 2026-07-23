package com.devcollab.knowledgecore.workspace.infrastructure;

import com.devcollab.knowledgecore.workspace.domain.Workspace;
import com.devcollab.knowledgecore.workspace.domain.WorkspaceMemberRepository;
import com.devcollab.knowledgecore.workspace.domain.WorkspaceRepository;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Profile("in-memory")
public class InMemoryWorkspaceRepository implements WorkspaceRepository {

    private final Map<UUID, Workspace> workspaces = new ConcurrentHashMap<>();
    private final WorkspaceMemberRepository memberRepository;

    public InMemoryWorkspaceRepository(
            WorkspaceMemberRepository memberRepository
    ) {
        this.memberRepository = memberRepository;
    }

    @Override
    public Workspace save(Workspace workspace) {
        workspaces.put(workspace.id(), workspace);
        return workspace;
    }

    @Override
    public Optional<Workspace> findById(UUID workspaceId) {
        return Optional.ofNullable(workspaces.get(workspaceId));
    }

    @Override
    public List<Workspace> findAllByUserId(UUID userId) {
        return workspaces.values().stream()
                .filter(workspace -> memberRepository
                        .findByWorkspaceIdAndUserId(workspace.id(), userId)
                        .isPresent())
                .sorted(Comparator.comparing(Workspace::createdAt))
                .toList();
    }

    @Override
    public void deleteById(UUID workspaceId) {
        workspaces.remove(workspaceId);
    }
}
