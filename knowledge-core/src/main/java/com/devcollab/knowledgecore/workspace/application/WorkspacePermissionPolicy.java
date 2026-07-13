package com.devcollab.knowledgecore.workspace.application;

import com.devcollab.knowledgecore.workspace.domain.WorkspaceMember;
import com.devcollab.knowledgecore.workspace.domain.WorkspaceRole;
import org.springframework.stereotype.Component;

/**
 * Centralized workspace permission policy.
 *
 * MVP product roles:
 * - no membership: no workspace permission
 * - MEMBER: document and block collaboration
 * - ADMIN: member management and collaboration
 *
 * Keep all role-to-permission mapping here so the product can add, remove,
 * or rename roles later without scattering role checks across services.
 */
@Component
public class WorkspacePermissionPolicy {

    public boolean canManageMembers(WorkspaceMember member) {
        return member.role() == WorkspaceRole.ADMIN;
    }

    public boolean isAdmin(WorkspaceMember member) {
        return member.role() == WorkspaceRole.ADMIN;
    }
}
