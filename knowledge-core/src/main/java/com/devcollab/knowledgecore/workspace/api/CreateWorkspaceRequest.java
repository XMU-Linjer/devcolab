package com.devcollab.knowledgecore.workspace.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWorkspaceRequest(
        @NotBlank(message = "工作空间名称不能为空")
        @Size(max = 100, message = "工作空间名称不能超过 100 个字符")
        String name
) {
}
