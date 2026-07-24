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

    DocumentStructure getDocumentStructure(
            UUID workspaceId,
            UUID documentId,
            boolean includeBlockContent,
            int maxBlocks,
            int maxContentChars,
            McpUserIdentity identity
    );

    BindingQueryResult getFileBindings(
            UUID workspaceId,
            UUID repositoryId,
            String filePath,
            int maxBindings,
            McpUserIdentity identity
    );

    List<SearchCandidate> searchDocuments(
            UUID workspaceId,
            String keyword,
            String scope,
            int limit,
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

    record DocumentStructure(
            UUID documentId,
            UUID workspaceId,
            String title,
            String documentType,
            String reviewStatus,
            java.time.Instant updatedAt,
            List<BlockInfo> blocks,
            boolean isTruncated,
            int omittedBlockCount,
            int omittedCharacterCount
    ) {
    }

    record BlockInfo(
            UUID blockId,
            String blockType,
            int sortOrder,
            long version,
            String plainText,
            String content,
            boolean isContentTruncated
    ) {
    }

    record BindingQueryResult(
            UUID workspaceId,
            UUID repositoryId,
            String filePath,
            boolean fileHasBindings,
            List<BindingInfo> bindings,
            boolean isTruncated,
            int omittedBindingCount
    ) {
    }

    record BindingInfo(
            UUID bindingId,
            String pathPattern,
            UUID documentId,
            String documentTitle,
            UUID blockId
    ) {
    }

    record SearchCandidate(
            String type,
            UUID documentId,
            String documentTitle,
            UUID blockId,
            String snippet,
            java.time.Instant updatedAt
    ) {
    }
}