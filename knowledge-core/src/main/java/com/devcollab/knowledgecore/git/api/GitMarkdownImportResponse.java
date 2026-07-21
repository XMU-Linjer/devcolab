package com.devcollab.knowledgecore.git.api;

import com.devcollab.knowledgecore.git.application.GitMarkdownImportResult;

public record GitMarkdownImportResponse(
        int importedDocuments,
        int skippedDocuments,
        int unavailableDocuments
) {
    public static GitMarkdownImportResponse from(GitMarkdownImportResult result) {
        return new GitMarkdownImportResponse(
                result.importedDocuments(),
                result.skippedDocuments(),
                result.unavailableDocuments()
        );
    }
}
