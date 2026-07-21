package com.devcollab.knowledgecore.git.application;

import com.devcollab.knowledgecore.git.domain.GitProvider;

public record RegisterGitRepositoryCommand(
        String name,
        GitProvider provider,
        String remoteUrl,
        String defaultBranch
) {
}
