package com.devcollab.worker.git;

public record CodeSymbolDependencyProjection(
        String sourceSymbolKey,
        String targetSymbolKey,
        String relationType,
        String evidenceFilePath
) {
}
