package com.devcollab.knowledgecore.git.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CodeBindingBatchQueryRequest(
        @NotEmpty @Size(max = 100) List<String> filePaths,
        @Size(max = 255) String revision,
        Boolean includeLegacy,
        @Min(1) @Max(1000) Integer maxBindings
) {
    public boolean includeLegacyOrDefault() {
        return includeLegacy == null || includeLegacy;
    }
}
