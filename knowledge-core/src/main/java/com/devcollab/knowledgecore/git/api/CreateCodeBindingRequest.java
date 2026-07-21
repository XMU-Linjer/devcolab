package com.devcollab.knowledgecore.git.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateCodeBindingRequest(
        @NotNull UUID repositoryId,
        UUID blockId,
        @NotBlank @Size(max = 1000) String pathPattern
) {
}
