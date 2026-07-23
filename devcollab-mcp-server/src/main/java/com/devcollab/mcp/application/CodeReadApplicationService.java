package com.devcollab.mcp.application;

import com.devcollab.mcp.client.KnowledgeCoreGateway;
import com.devcollab.mcp.error.McpToolErrorCode;
import com.devcollab.mcp.error.McpToolException;
import com.devcollab.mcp.governance.ContextBudgetPolicy;
import com.devcollab.mcp.security.McpUserIdentity;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CodeReadApplicationService {

    private final KnowledgeCoreGateway coreGateway;
    private final ContextBudgetPolicy budgetPolicy;

    public CodeReadApplicationService(
            KnowledgeCoreGateway coreGateway,
            ContextBudgetPolicy budgetPolicy
    ) {
        this.coreGateway = coreGateway;
        this.budgetPolicy = budgetPolicy;
    }

    public Map<String, Object> read(
            UUID workspaceId,
            UUID repositoryId,
            String path,
            Integer startLine,
            Integer endLine,
            boolean includeExistingBindings,
            McpUserIdentity identity
    ) {
        budgetPolicy.validateRepositoryPath(path);
        KnowledgeCoreGateway.RepositorySource source =
                coreGateway.readRepositorySource(workspaceId, repositoryId, path, identity);
        if (!source.readable() || source.content() == null) {
            throw new McpToolException(
                    McpToolErrorCode.UNSUPPORTED_FILE_TYPE,
                    "The repository file is not readable text"
            );
        }

        ContextBudgetPolicy.BudgetedContent content =
                budgetPolicy.limit(source.content(), startLine, endLine);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workspaceId", workspaceId.toString());
        result.put("repositoryId", source.repositoryId().toString());
        result.put("path", source.path());
        result.put("commitHash", source.commitSha());
        result.put("language", source.language());
        result.put("sizeBytes", source.sizeBytes());
        result.put("startLine", content.startLine());
        result.put("endLine", content.endLine());
        result.put("totalLines", content.totalLines());
        result.put("content", content.content());
        result.put("truncated", content.truncated());
        result.put("omittedLineCount", content.omittedLineCount());
        result.put("omittedCharacterCount", content.omittedCharacterCount());
        result.put("existingBindings", List.of());
        result.put("existingBindingsAvailable", false);
        result.put("existingBindingsRequested", includeExistingBindings);
        return result;
    }
}
