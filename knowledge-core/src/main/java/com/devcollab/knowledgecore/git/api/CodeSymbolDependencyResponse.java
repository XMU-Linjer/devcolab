package com.devcollab.knowledgecore.git.api;

import com.devcollab.knowledgecore.git.domain.CodeSymbolDependency;

public record CodeSymbolDependencyResponse(
        String sourceSymbolKey,
        String targetSymbolKey,
        String relationType,
        String evidenceFilePath
) {
    public static CodeSymbolDependencyResponse from(
            CodeSymbolDependency dependency
    ) {
        return new CodeSymbolDependencyResponse(
                dependency.sourceSymbolKey(), dependency.targetSymbolKey(),
                dependency.relationType(), dependency.evidenceFilePath()
        );
    }
}
