package com.devcollab.knowledgecore.git.api;

import com.devcollab.knowledgecore.git.domain.GitProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterGitRepositoryRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull GitProvider provider,
        @NotBlank @Size(max = 500) String remoteUrl,
        @NotBlank @Size(max = 200) String defaultBranch
) {
}
