package com.devcollab.mcp.client;

import com.devcollab.mcp.security.McpUserIdentity;

import java.util.List;
import java.util.UUID;

public interface KnowledgeCoreGateway {

    WorkspaceContext getWorkspaceContext(UUID workspaceId, McpUserIdentity identity);

    RepositorySource readRepositorySource(
            UUID workspaceId,
            UUID repositoryId,
            String path,
            McpUserIdentity identity
    );

    record WorkspaceContext(
            UUID workspaceId,
            String name,
            String currentUserRole,
            List<RepositoryContext> repositories
    ) {
    }

    record RepositoryContext(
            UUID repositoryId,
            String name,
            String provider,
            String remoteUrl,
            String defaultBranch,
            String syncStatus,
            String lastSyncedCommit
    ) {
    }

    record RepositorySource(
            UUID repositoryId,
            String commitSha,
            String path,
            long sizeBytes,
            String language,
            boolean readable,
            String content
    ) {
    }
}
