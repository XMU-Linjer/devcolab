package com.devcollab.knowledgecore.git.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CodeMetadataBatchRequest(
        @NotBlank String revision,
        @NotEmpty @Size(max = 100) List<@NotBlank String> filePaths
) {
}
