package com.devcollab.knowledgecore.git.api;

import com.devcollab.knowledgecore.git.domain.GitRepositoryFile;

import java.util.UUID;

public record GitRepositoryFileResponse(
        UUID id,
        String path,
        String blobSha,
        long sizeBytes,
        String language,
        boolean readable
) {
    public static GitRepositoryFileResponse from(GitRepositoryFile file) {
        return new GitRepositoryFileResponse(
                file.id(), file.path(), file.blobSha(),
                file.sizeBytes(), file.language(), file.contentText() != null
        );
    }
}
