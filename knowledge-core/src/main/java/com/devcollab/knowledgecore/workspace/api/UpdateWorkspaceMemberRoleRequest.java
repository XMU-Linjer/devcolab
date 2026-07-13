package com.devcollab.knowledgecore.workspace.api;

import com.devcollab.knowledgecore.workspace.domain.WorkspaceRole;
import jakarta.validation.constraints.NotNull;

public record UpdateWorkspaceMemberRoleRequest(
        @NotNull(message = "成员角色不能为空")
        WorkspaceRole role
) {
}
