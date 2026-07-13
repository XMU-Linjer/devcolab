package com.devcollab.knowledgecore.workspace.infrastructure;

import com.devcollab.knowledgecore.workspace.domain.WorkspaceMember;
import com.devcollab.knowledgecore.workspace.domain.WorkspaceMemberRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
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

    private record MemberKey(UUID workspaceId, UUID userId) {
    }
}
