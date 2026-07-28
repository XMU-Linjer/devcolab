package com.devcollab.knowledgecore.git.application;

import com.devcollab.knowledgecore.git.domain.GitRepositoryFile;

import java.util.List;
import java.util.UUID;

public record RepositoryFilePageResult(
        UUID workspaceId,
        UUID repositoryId,
        String revision,
        String pathPrefix,
        boolean recursive,
        List<GitRepositoryFile> files,
        String nextCursor,
        boolean hasMore
) {
}
