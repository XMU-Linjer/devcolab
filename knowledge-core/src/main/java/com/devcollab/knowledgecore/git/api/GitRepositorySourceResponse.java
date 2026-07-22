package com.devcollab.knowledgecore.git.api;

import com.devcollab.knowledgecore.git.application.GitRepositorySourceDetails;

import java.util.List;
import java.util.UUID;

public record GitRepositorySourceResponse(
        UUID repositoryId,
        String commitSha,
        String path,
        String blobSha,
        long sizeBytes,
        String language,
        boolean readable,
        String content,
        List<CodeSymbolResponse> symbols
) {
    public static GitRepositorySourceResponse from(
            GitRepositorySourceDetails details
    ) {
        var file = details.file();
        return new GitRepositorySourceResponse(
                details.repositoryId(),
                details.commitSha(),
                file.path(),
                file.blobSha(),
                file.sizeBytes(),
                file.language(),
                file.contentText() != null,
                file.contentText(),
                details.symbols().stream().map(CodeSymbolResponse::from).toList()
        );
    }
}
