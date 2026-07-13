package com.devcollab.knowledgecore.document.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record UpdateDocumentBlockRequest(
        @Valid
        @NotNull(message = "Block 内容不能为空")
        DocumentBlockContentRequest content
) {
}
