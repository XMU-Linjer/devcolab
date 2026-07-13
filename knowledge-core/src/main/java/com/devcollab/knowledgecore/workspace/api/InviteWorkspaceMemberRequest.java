package com.devcollab.knowledgecore.workspace.api;

import com.devcollab.knowledgecore.workspace.domain.WorkspaceRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record InviteWorkspaceMemberRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 4, max = 32, message = "用户名长度必须在 4 到 32 个字符之间")
        @Pattern(
                regexp = "^[a-zA-Z0-9_]+$",
                message = "用户名只能包含字母、数字和下划线"
        )
        String username,

        @NotNull(message = "成员角色不能为空")
        WorkspaceRole role
) {
}
