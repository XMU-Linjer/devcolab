package com.devcollab.knowledgecore.document.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DocumentBlockContentRequest(
        @NotBlank(message = "段落内容不能为空")
        @Size(max = 20000, message = "段落内容不能超过 20000 个字符")
        String text
) {
}
