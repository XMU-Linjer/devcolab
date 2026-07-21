package com.devcollab.knowledgecore.git.application;

import com.devcollab.knowledgecore.git.domain.CodeFileDependency;
import com.devcollab.knowledgecore.git.domain.CodeSymbol;
import com.devcollab.knowledgecore.git.domain.CodeSymbolDependency;

import java.util.List;

public record CodeGraphDetails(
        List<CodeSymbol> symbols,
        List<CodeSymbolDependency> symbolDependencies,
        List<CodeFileDependency> fileDependencies
) {
}
