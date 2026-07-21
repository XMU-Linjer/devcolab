package com.devcollab.worker.git;

import java.util.List;

public record CodeGraphProjection(
        List<CodeSymbolProjection> symbols,
        List<CodeSymbolDependencyProjection> symbolDependencies,
        List<CodeFileDependencyProjection> fileDependencies,
        List<String> parseFailures
) {
}
