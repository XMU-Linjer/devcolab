package com.devcollab.knowledgecore.git.api;

import com.devcollab.knowledgecore.git.domain.GitProvider;
import com.devcollab.knowledgecore.git.domain.GitRepository;

import java.time.Instant;
import java.util.UUID;

public record GitRepositoryResponse(
        UUID id,
        UUID workspaceId,
        String name,
        GitProvider provider,
        String remoteUrl,
        String defaultBranch,
        Instant createdAt
) {
    public static GitRepositoryResponse from(GitRepository repository) {
        return new GitRepositoryResponse(
                repository.id(), repository.workspaceId(), repository.name(),
                repository.provider(), repository.remoteUrl(),
                repository.defaultBranch(), repository.createdAt()
        );
    }
}
