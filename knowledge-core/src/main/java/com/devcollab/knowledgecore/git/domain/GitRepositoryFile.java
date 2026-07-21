package com.devcollab.knowledgecore.git.domain;

import java.util.UUID;

public record GitRepositoryFile(
        UUID id,
        UUID repositoryId,
        String path,
        String blobSha,
        long sizeBytes,
        String language,
        String contentText
) {
    public GitRepositoryFile(
            UUID id,
            UUID repositoryId,
            String path,
            String blobSha,
            long sizeBytes,
            String language
    ) {
        this(id, repositoryId, path, blobSha, sizeBytes, language, null);
    }
}
