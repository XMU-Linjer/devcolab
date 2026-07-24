package com.devcollab.mcp.capability.tool;

import com.devcollab.mcp.application.FindCandidatesApplicationService;
import com.devcollab.mcp.capability.McpToolContributor;
import com.devcollab.mcp.config.McpProperties;
import com.devcollab.mcp.governance.AuditedToolExecutor;
import com.devcollab.mcp.error.McpToolErrorMapper;
import com.devcollab.mcp.security.McpTransportIdentity;
import com.devcollab.mcp.security.McpUserIdentity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class FindCandidatesToolContributor implements McpToolContributor {

    public static final String TOOL_NAME = "devcollab.document.find_candidates";

    private final FindCandidatesApplicationService applicationService;
    private final AuditedToolExecutor auditedToolExecutor;
    private final McpToolErrorMapper errorMapper;
    private final McpProperties mcpProperties;
    private final ObjectMapper objectMapper;

    public FindCandidatesToolContributor(
            FindCandidatesApplicationService applicationService,
            AuditedToolExecutor auditedToolExecutor,
            McpToolErrorMapper errorMapper,
            McpProperties mcpProperties,
            ObjectMapper objectMapper
    ) {
        this.applicationService = applicationService;
        this.auditedToolExecutor = auditedToolExecutor;
        this.errorMapper = errorMapper;
        this.mcpProperties = mcpProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<McpServerFeatures.SyncToolSpecification> tools() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name(TOOL_NAME)
                .title("Find document candidates")
                .description("Search for documents within a workspace by keyword query")
                .inputSchema(McpToolSchemas.findCandidatesInput(mcpProperties.maxDocumentQueryCharacters()))
                .outputSchema(McpToolSchemas.findCandidatesOutput())
                .annotations(WorkspaceContextToolContributor.readOnlyAnnotations())
                .build();
                
        return List.of(errorMapper.protect(tool, (exchange, request) -> {
            McpUserIdentity identity = McpTransportIdentity.require(exchange);
            Map<String, Object> arguments = request.arguments();
            
            UUID workspaceId = McpToolArguments.requiredUuid(arguments, "workspaceId");
            String query = McpToolArguments.requiredString(arguments, "query");
            String scope = arguments.get("scope") instanceof String s ? s : null;
            Integer maxResults = McpToolArguments.optionalInteger(arguments, "maxResults");

            Map<String, Object> result = auditedToolExecutor.execute(
                    TOOL_NAME, identity, workspaceId, null, arguments,
                    () -> applicationService.findCandidates(workspaceId, query, scope, maxResults, identity)
            );
            return success(result);
        }));
    }

    private McpSchema.CallToolResult success(Map<String, Object> result) {
        try {
            return McpSchema.CallToolResult.builder()
                    .addTextContent(objectMapper.writeValueAsString(result))
                    .structuredContent(result)
                    .isError(false)
                    .build();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize", exception);
        }
    }
}
