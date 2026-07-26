package com.devcollab.mcp.application;

import com.devcollab.mcp.client.KnowledgeCoreGateway;
import com.devcollab.mcp.config.ReviewSubmissionProperties;
import com.devcollab.mcp.error.McpToolErrorCode;
import com.devcollab.mcp.error.McpToolException;
import com.devcollab.mcp.security.McpUserIdentity;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReviewSubmissionApplicationService {

    private final KnowledgeCoreGateway gateway;
    private final ReviewSubmissionProperties properties;

    public ReviewSubmissionApplicationService(
            KnowledgeCoreGateway gateway,
            ReviewSubmissionProperties properties
    ) {
        this.gateway = gateway;
        this.properties = properties;
    }

    public Map<String, Object> submit(
            UUID workspaceId,
            Map<String, Object> arguments,
            McpUserIdentity identity
    ) {
        validateText(arguments.get("clientRequestId"), 100, "clientRequestId");
        validateText(
                arguments.get("summary"),
                properties.maxSummaryCharacters(),
                "summary"
        );
        validateText(
                arguments.get("rationale"),
                properties.maxRationaleCharacters(),
                "rationale"
        );
        List<?> operations = list(arguments.get("operations"), "operations");
        if (operations.isEmpty()
                || operations.size() > properties.maxOperations()) {
            throw invalid(
                    "operations must contain 1 to "
                            + properties.maxOperations() + " entries"
            );
        }
        for (Object operation : operations) {
            Map<?, ?> item = map(operation, "operation");
            validateText(item.get("clientOperationId"), 100, "clientOperationId");
            validateOptionalText(
                    item.get("proposedPlainText"),
                    properties.maxProposedCharacters(),
                    "proposedPlainText"
            );
            Object proposedContent = item.get("proposedContent");
            if (proposedContent != null
                    && proposedContent.toString().length()
                    > properties.maxProposedCharacters() * 4) {
                throw invalid("proposedContent exceeds the configured budget");
            }
        }
        Object evidenceValue = arguments.get("evidence");
        List<?> evidence = evidenceValue == null
                ? List.of() : list(evidenceValue, "evidence");
        if (evidence.size() > properties.maxEvidence()) {
            throw invalid(
                    "evidence must not exceed "
                            + properties.maxEvidence() + " entries"
            );
        }
        for (Object evidenceItem : evidence) {
            Map<?, ?> item = map(evidenceItem, "evidence item");
            validateText(
                    item.get("description"),
                    properties.maxDescriptionCharacters(),
                    "evidence.description"
            );
        }

        Map<String, Object> body = new LinkedHashMap<>(arguments);
        body.remove("workspaceId");
        return gateway.submitDocumentChange(workspaceId, body, identity);
    }

    private void validateText(Object value, int max, String field) {
        if (!(value instanceof String text)
                || text.trim().isEmpty()
                || text.codePointCount(0, text.length()) > max) {
            throw invalid(field + " is required and exceeds no configured limit");
        }
    }

    private void validateOptionalText(Object value, int max, String field) {
        if (value != null) {
            validateText(value, max, field);
        }
    }

    private List<?> list(Object value, String field) {
        if (!(value instanceof List<?> list)) {
            throw invalid(field + " must be an array");
        }
        return list;
    }

    private Map<?, ?> map(Object value, String field) {
        if (!(value instanceof Map<?, ?> map)) {
            throw invalid(field + " must be an object");
        }
        return map;
    }

    private McpToolException invalid(String message) {
        return new McpToolException(McpToolErrorCode.INVALID_ARGUMENT, message);
    }
}
