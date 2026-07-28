package com.devcollab.knowledgecore.git.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CodeBindingBatchQueryRequest(
        @NotEmpty @Size(max = 100) List<String> filePaths
) {
}
