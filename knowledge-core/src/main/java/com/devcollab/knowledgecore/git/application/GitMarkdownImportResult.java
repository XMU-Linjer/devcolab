package com.devcollab.knowledgecore.git.application;

public record GitMarkdownImportResult(
        int importedDocuments,
        int skippedDocuments,
        int unavailableDocuments
) {
}
