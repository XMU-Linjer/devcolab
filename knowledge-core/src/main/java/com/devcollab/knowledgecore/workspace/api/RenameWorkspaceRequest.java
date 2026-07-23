package com.devcollab.knowledgecore.workspace.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameWorkspaceRequest(
        @NotBlank(message = "工作区名称不能为空")
        @Size(max = 100, message = "工作区名称不能超过 100 个字符")
        String name
) {
}
