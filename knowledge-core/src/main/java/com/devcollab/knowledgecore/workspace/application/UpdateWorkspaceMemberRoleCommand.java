package com.devcollab.knowledgecore.workspace.application;

import com.devcollab.knowledgecore.workspace.domain.WorkspaceRole;

public record UpdateWorkspaceMemberRoleCommand(
        WorkspaceRole role
) {
}
