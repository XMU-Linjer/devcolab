package com.devcollab.knowledgecore.git.api;

import com.devcollab.knowledgecore.git.application.RepositoryChangePageResult;

import java.util.List;
import java.util.UUID;

public record RepositoryChangePageResponse(
        UUID workspaceId,
        UUID repositoryId,
        UUID changeId,
        String changeType,
        String commitSha,
        List<ChangedFile> files,
        String nextCursor,
        boolean hasMore
) {
    public static RepositoryChangePageResponse from(RepositoryChangePageResult result) {
        return new RepositoryChangePageResponse(
                result.workspaceId(), result.repositoryId(), result.changeId(),
                result.changeType(), result.commitSha(),
                result.files().stream().map(file -> new ChangedFile(
                        file.status().name(), file.filePath(), file.oldPath(),
                        file.binaryFile()
                )).toList(),
                result.nextCursor(), result.hasMore()
        );
    }

    public record ChangedFile(
            String status,
            String filePath,
            String oldPath,
            boolean binaryFile
    ) {
    }
}
