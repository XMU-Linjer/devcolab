package com.devcollab.knowledgecore.git.api;

import com.devcollab.knowledgecore.git.domain.CodeSymbol;

public record CodeSymbolResponse(
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
    public static CodeSymbolResponse from(CodeSymbol symbol) {
        return new CodeSymbolResponse(
                symbol.filePath(), symbol.symbolKey(), symbol.language(),
                symbol.symbolKind(), symbol.qualifiedName(), symbol.simpleName(),
                symbol.signature(), symbol.parentSymbolKey(), symbol.startLine(),
                symbol.endLine()
        );
    }
}
