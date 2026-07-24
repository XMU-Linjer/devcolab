package com.devcollab.mcp.application;

import com.devcollab.mcp.client.KnowledgeCoreGateway;
import com.devcollab.mcp.config.McpProperties;
import com.devcollab.mcp.error.McpToolErrorCode;
import com.devcollab.mcp.error.McpToolException;
import com.devcollab.mcp.security.McpUserIdentity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FindCandidatesApplicationService {

    private final KnowledgeCoreGateway knowledgeCoreGateway;
    private final McpProperties mcpProperties;

    public FindCandidatesApplicationService(KnowledgeCoreGateway knowledgeCoreGateway, McpProperties mcpProperties) {
        this.knowledgeCoreGateway = knowledgeCoreGateway;
        this.mcpProperties = mcpProperties;
    }

    public Map<String, Object> findCandidates(
            UUID workspaceId,
            String query,
            String scope,
            Integer maxResultsArg,
            McpUserIdentity identity
    ) {
        validateQuery(query);

        String effectiveScope = scope != null ? scope : "ALL";
        int maxResults = maxResultsArg != null
                ? Math.min(maxResultsArg, mcpProperties.maxCandidates())
                : mcpProperties.maxCandidates();

        List<KnowledgeCoreGateway.SearchCandidate> candidates = knowledgeCoreGateway.searchDocuments(
                workspaceId,
                query.trim(),
                effectiveScope,
                maxResults,
                identity
        );

        boolean truncated = candidates.size() > maxResults;
        int omittedCount = 0;
        if (truncated) {
            omittedCount = candidates.size() - maxResults;
            candidates = candidates.subList(0, maxResults);
        }

        List<Map<String, Object>> candidateList = candidates.stream()
                .map(c -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("type", c.type());
                    map.put("documentId", c.documentId());
                    map.put("documentTitle", c.documentTitle());
                    if (c.blockId() != null) {
                        map.put("blockId", c.blockId());
                    }
                    if (c.snippet() != null) {
                        map.put("snippet", c.snippet());
                    }
                    if (c.updatedAt() != null) {
                        map.put("updatedAt", c.updatedAt().toString());
                    }
                    return map;
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("workspaceId", workspaceId);
        result.put("query", query.trim());
        result.put("scope", effectiveScope);
        result.put("candidates", candidateList);
        result.put("totalResults", candidateList.size());
        result.put("truncated", truncated);
        result.put("omittedCount", omittedCount);
        return result;
    }

    private void validateQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new McpToolException(McpToolErrorCode.INVALID_DOCUMENT_QUERY, "Query cannot be blank");
        }
        int codePointCount = query.trim().codePointCount(0, query.trim().length());
        if (codePointCount > mcpProperties.maxDocumentQueryCharacters()) {
            throw new McpToolException(
                    McpToolErrorCode.INVALID_DOCUMENT_QUERY,
                    "Query exceeds maximum length of " + mcpProperties.maxDocumentQueryCharacters() + " characters"
            );
        }
    }
}
