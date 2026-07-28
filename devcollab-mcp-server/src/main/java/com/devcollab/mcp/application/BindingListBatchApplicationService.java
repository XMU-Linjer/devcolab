package com.devcollab.mcp.application;

import com.devcollab.mcp.client.KnowledgeCoreGateway;
import com.devcollab.mcp.config.McpProperties;
import com.devcollab.mcp.error.McpToolErrorCode;
import com.devcollab.mcp.error.McpToolException;
import com.devcollab.mcp.security.McpUserIdentity;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BindingListBatchApplicationService {

    private final KnowledgeCoreGateway gateway;
    private final McpProperties properties;

    public BindingListBatchApplicationService(
            KnowledgeCoreGateway gateway,
            McpProperties properties
    ) {
        this.gateway = gateway;
        this.properties = properties;
    }

    public Map<String, Object> listBindings(
            UUID workspaceId,
            UUID repositoryId,
            List<String> filePaths,
            McpUserIdentity identity
    ) {
        if (filePaths.isEmpty() || filePaths.size() > properties.maxBindingBatchPaths()) {
            throw new McpToolException(
                    McpToolErrorCode.INVALID_ARGUMENT,
                    "filePaths exceeds the binding batch size"
            );
        }
        List<String> normalized = filePaths.stream()
                .map(path -> RepositoryPathPolicy.normalize(path, false))
                .distinct()
                .toList();
        var batch = gateway.getFileBindingsBatch(
                workspaceId, repositoryId, normalized, identity
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workspaceId", batch.workspaceId());
        result.put("repositoryId", batch.repositoryId());
        result.put("files", batch.files().stream().map(file -> {
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("filePath", file.filePath());
            group.put("bindings", file.bindings().stream().map(binding -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("bindingId", binding.bindingId());
                item.put("repositoryId", binding.repositoryId());
                item.put("documentId", binding.documentId());
                item.put("blockId", binding.blockId());
                item.put("pathPattern", binding.pathPattern());
                return item;
            }).toList());
            return group;
        }).toList());
        return result;
    }
}
