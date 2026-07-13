package com.devcollab.knowledgecore.workspace.infrastructure;

import com.devcollab.knowledgecore.workspace.domain.WorkspaceMember;
import com.devcollab.knowledgecore.workspace.domain.WorkspaceMemberRepository;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.devcollab.knowledgecore.workspace.domain.WorkspaceRole;

@Repository
@Profile("in-memory")
public class InMemoryWorkspaceMemberRepository
        implements WorkspaceMemberRepository {

    private final Map<MemberKey, WorkspaceMember> members =
            new ConcurrentHashMap<>();

    @Override
    public WorkspaceMember save(WorkspaceMember member) {
        members.put(
                new MemberKey(member.workspaceId(), member.userId()),
                member
        );
        return member;
    }

    @Override
    public Optional<WorkspaceMember> findByWorkspaceIdAndUserId(
            UUID workspaceId,
            UUID userId
    ) {
        return Optional.ofNullable(
                members.get(new MemberKey(workspaceId, userId))
        );
    }

    @Override
    public List<WorkspaceMember> findAllByWorkspaceId(UUID workspaceId) {
        return members.values().stream()
                .filter(member -> workspaceId.equals(member.workspaceId()))
                .toList();
    }

    @Override
    public boolean existsByWorkspaceIdAndUserId(
            UUID workspaceId,
            UUID userId
    ) {
        return members.containsKey(new MemberKey(workspaceId, userId));
    }

    @Override
    public long countByWorkspaceIdAndRole(
            UUID workspaceId,
            WorkspaceRole role
    ) {
        return members.values().stream()
                .filter(member -> workspaceId.equals(member.workspaceId()))
                .filter(member -> role == member.role())
                .count();
    }

    @Override
    public void deleteByWorkspaceIdAndUserId(UUID workspaceId, UUID userId) {
        members.remove(new MemberKey(workspaceId, userId));
    }

    private record MemberKey(UUID workspaceId, UUID userId) {
    }
}
