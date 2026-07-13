package com.devcollab.knowledgecore.document.api;

import com.devcollab.knowledgecore.document.domain.DocumentBlockType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CreateDocumentBlockRequest(
        @NotNull(message = "Block 类型不能为空")
        DocumentBlockType type,

        @Valid
        @NotNull(message = "Block 内容不能为空")
        DocumentBlockContentRequest content
) {
}
