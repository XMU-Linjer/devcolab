package com.devcollab.knowledgecore.git.domain;

import java.util.UUID;

public record CodeSymbol(
        UUID id,
        UUID repositoryId,
        String filePath,
        String symbolKey,
        String language,
        String symbolKind,
        String qualifiedName,
        String simpleName,
        String signature,
        String parentSymbolKey,
        Integer startLine,
        Integer endLine
) {
}
