package com.devcollab.knowledgecore.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "用户名不能为空")
        @Size(min = 4, max = 32, message = "用户名长度必须在 4 到 32 个字符之间")
        @Pattern(
                regexp = "^[a-zA-Z0-9_]+$",
                message = "用户名只能包含字母、数字和下划线"
        )
        String username,

        @NotBlank(message = "显示名称不能为空")
        @Size(max = 50, message = "显示名称不能超过 50 个字符")
        String displayName,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 72, message = "密码长度必须在 8 到 72 个字符之间")
        String password
) {
}