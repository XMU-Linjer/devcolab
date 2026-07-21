package com.devcollab.knowledgecore.git.api;

import com.devcollab.knowledgecore.git.application.CodeGraphDetails;

import java.util.List;

public record CodeGraphResponse(
        List<CodeSymbolResponse> symbols,
        List<CodeSymbolDependencyResponse> symbolDependencies,
        List<CodeFileDependencyResponse> fileDependencies
) {
    public static CodeGraphResponse from(CodeGraphDetails details) {
        return new CodeGraphResponse(
                details.symbols().stream().map(CodeSymbolResponse::from).toList(),
                details.symbolDependencies().stream()
                        .map(CodeSymbolDependencyResponse::from).toList(),
                details.fileDependencies().stream()
                        .map(CodeFileDependencyResponse::from).toList()
        );
    }
}
