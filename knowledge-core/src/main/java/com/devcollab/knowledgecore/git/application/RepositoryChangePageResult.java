package com.devcollab.knowledgecore.git.application;

import com.devcollab.knowledgecore.git.domain.GitFileChangeType;

import java.util.List;
import java.util.UUID;

public record RepositoryChangePageResult(
        UUID workspaceId,
        UUID repositoryId,
        UUID changeId,
        String changeType,
        String commitSha,
        List<ChangedFile> files,
        String nextCursor,
        boolean hasMore
) {
    public record ChangedFile(
            UUID diffId,
            GitFileChangeType status,
            String filePath,
            String oldPath,
            boolean binaryFile
    ) {
    }
}
