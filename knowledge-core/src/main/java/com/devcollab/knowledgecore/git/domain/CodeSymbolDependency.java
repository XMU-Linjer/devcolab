package com.devcollab.knowledgecore.git.domain;

import java.util.UUID;

public record CodeSymbolDependency(
        UUID id,
        UUID repositoryId,
        String sourceSymbolKey,
        String targetSymbolKey,
        String relationType,
        String evidenceFilePath
) {
}
