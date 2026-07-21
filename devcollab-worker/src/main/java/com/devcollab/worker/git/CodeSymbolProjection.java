package com.devcollab.worker.git;

public record CodeSymbolProjection(
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
