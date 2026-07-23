package com.devcollab.mcp.application;

import com.devcollab.mcp.client.KnowledgeCoreGateway;
import com.devcollab.mcp.security.McpUserIdentity;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class WorkspaceContextApplicationService {

    private final KnowledgeCoreGateway coreGateway;

    public WorkspaceContextApplicationService(KnowledgeCoreGateway coreGateway) {
        this.coreGateway = coreGateway;
    }

    public Map<String, Object> getContext(UUID workspaceId, McpUserIdentity identity) {
        KnowledgeCoreGateway.WorkspaceContext context =
                coreGateway.getWorkspaceContext(workspaceId, identity);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workspaceId", context.workspaceId().toString());
        result.put("name", context.name());
        result.put("currentUserRole", context.currentUserRole());
        result.put("repositories", context.repositories().stream().map(repository -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("repositoryId", repository.repositoryId().toString());
            item.put("name", repository.name());
            item.put("provider", repository.provider());
            item.put("remoteUrl", repository.remoteUrl());
            item.put("defaultBranch", repository.defaultBranch());
            item.put("syncStatus", repository.syncStatus());
            if (repository.lastSyncedCommit() != null) {
                item.put("lastSyncedCommit", repository.lastSyncedCommit());
            }
            return item;
        }).toList());
        return result;
    }
}
