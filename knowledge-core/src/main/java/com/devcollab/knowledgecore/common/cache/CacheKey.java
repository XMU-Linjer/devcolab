package com.devcollab.knowledgecore.common.cache;

import java.util.UUID;

/**
 * Centralized cache key definitions.
 *
 * <p>All Redis keys are managed here so that key patterns,
 * namespaces, and eviction logic stay consistent across services.
 */
public final class CacheKey {

    private CacheKey() {
    }

    /**
     * Cached workspace membership lookup.
     * <p>Used by {@code WorkspaceApplicationService#requireMembership}
     * and permission checks throughout the document module.
     */
    public static String workspaceMember(UUID workspaceId, UUID userId) {
        return "workspace:member:" + workspaceId + ":" + userId;
    }

    /**
     * Cached document tree for a workspace.
     * <p>Used by {@code GET /api/v1/workspaces/{workspaceId}/documents/tree}.
     */
    public static String documentTree(UUID workspaceId) {
        return "workspace:documents:tree:" + workspaceId;
    }
}