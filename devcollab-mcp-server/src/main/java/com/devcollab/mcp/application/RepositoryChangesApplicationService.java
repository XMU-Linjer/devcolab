package com.devcollab.mcp.application;

import com.devcollab.mcp.client.KnowledgeCoreGateway;
import com.devcollab.mcp.config.McpProperties;
import com.devcollab.mcp.error.McpToolErrorCode;
import com.devcollab.mcp.error.McpToolException;
import com.devcollab.mcp.security.McpUserIdentity;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class RepositoryChangesApplicationService {

    private final KnowledgeCoreGateway gateway;
    private final McpProperties properties;

    public RepositoryChangesApplicationService(
            KnowledgeCoreGateway gateway,
            McpProperties properties
    ) {
        this.gateway = gateway;
        this.properties = properties;
    }

    public Map<String, Object> listChanges(
            UUID workspaceId,
            UUID repositoryId,
            String cursor,
            int limit,
            McpUserIdentity identity
    ) {
        if (limit < 1 || limit > properties.maxRepositoryPageSize()) {
            throw new McpToolException(
                    McpToolErrorCode.INVALID_ARGUMENT,
                    "limit exceeds the repository page size"
            );
        }
        var page = gateway.listRepositoryChanges(
                workspaceId, repositoryId, cursor, limit, identity
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workspaceId", page.workspaceId());
        result.put("repositoryId", page.repositoryId());
        result.put("changeId", page.changeId());
        result.put("changeType", page.changeType());
        result.put("commitSha", page.commitSha());
        result.put("files", page.files().stream().map(file -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("status", file.status());
            item.put("filePath", file.filePath());
            item.put("oldPath", file.oldPath());
            item.put("binaryFile", file.binaryFile());
            return item;
        }).toList());
        result.put("nextCursor", page.nextCursor());
        result.put("hasMore", page.hasMore());
        return result;
    }
}
