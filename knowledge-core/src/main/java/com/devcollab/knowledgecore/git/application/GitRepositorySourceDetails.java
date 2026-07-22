package com.devcollab.knowledgecore.git.application;

import com.devcollab.knowledgecore.git.domain.CodeSymbol;
import com.devcollab.knowledgecore.git.domain.GitRepositoryFile;

import java.util.List;
import java.util.UUID;

public record GitRepositorySourceDetails(
        UUID repositoryId,
        String commitSha,
        GitRepositoryFile file,
        List<CodeSymbol> symbols
) {
}
