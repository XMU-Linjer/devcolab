package com.devcollab.mcp.capability.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class McpToolSchemas {

    private McpToolSchemas() {
    }

    static Map<String, Object> workspaceContextInput() {
        return objectSchema(
                Map.of("workspaceId", uuidProperty("DevCollab workspace identifier")),
                List.of("workspaceId")
        );
    }

    static Map<String, Object> codeReadInput(int maxPathCharacters) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("workspaceId", uuidProperty("DevCollab workspace identifier"));
        properties.put("repositoryId", uuidProperty("Registered repository identifier"));
        properties.put("path", Map.of(
                "type", "string",
                "description", "Repository-relative file path",
                "minLength", 1,
                "maxLength", maxPathCharacters
        ));
        properties.put("startLine", Map.of("type", "integer", "minimum", 1));
        properties.put("endLine", Map.of("type", "integer", "minimum", 1));
        properties.put("includeExistingBindings", Map.of(
                "type", "boolean",
                "default", false,
                "description", "Request existing links when that backend capability is available"
        ));
        return objectSchema(properties, List.of("workspaceId", "repositoryId", "path"));
    }

    static Map<String, Object> workspaceContextOutput() {
        Map<String, Object> repository = objectSchema(
                Map.of(
                        "repositoryId", uuidProperty("Repository identifier"),
                        "name", Map.of("type", "string"),
                        "provider", Map.of("type", "string"),
                        "remoteUrl", Map.of("type", "string"),
                        "defaultBranch", Map.of("type", "string"),
                        "syncStatus", Map.of("type", "string"),
                        "lastSyncedCommit", Map.of("type", "string")
                ),
                List.of("repositoryId", "name", "provider", "remoteUrl", "defaultBranch", "syncStatus")
        );
        return toolOutputSchema(
                Map.of(
                        "workspaceId", uuidProperty("Workspace identifier"),
                        "name", Map.of("type", "string"),
                        "currentUserRole", Map.of("type", "string"),
                        "repositories", Map.of("type", "array", "items", repository)
                ),
                List.of("workspaceId", "name", "currentUserRole", "repositories")
        );
    }

    static Map<String, Object> codeReadOutput() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("workspaceId", uuidProperty("Workspace identifier"));
        properties.put("repositoryId", uuidProperty("Repository identifier"));
        properties.put("path", Map.of("type", "string"));
        properties.put("commitHash", Map.of("type", "string"));
        properties.put("language", Map.of("type", List.of("string", "null")));
        properties.put("sizeBytes", Map.of("type", "integer"));
        properties.put("startLine", Map.of("type", "integer"));
        properties.put("endLine", Map.of("type", "integer"));
        properties.put("totalLines", Map.of("type", "integer"));
        properties.put("content", Map.of("type", "string"));
        properties.put("truncated", Map.of("type", "boolean"));
        properties.put("omittedLineCount", Map.of("type", "integer"));
        properties.put("omittedCharacterCount", Map.of("type", "integer"));
        properties.put("existingBindings", Map.of("type", "array", "items", Map.of("type", "object")));
        properties.put("existingBindingsAvailable", Map.of("type", "boolean"));
        properties.put("existingBindingsRequested", Map.of("type", "boolean"));
        return toolOutputSchema(
                properties,
                List.of(
                        "workspaceId", "repositoryId", "path", "commitHash", "sizeBytes",
                        "startLine", "endLine", "totalLines", "content", "truncated",
                        "omittedLineCount", "omittedCharacterCount", "existingBindings",
                        "existingBindingsAvailable", "existingBindingsRequested"
                )
        );
    }
    
    static Map<String, Object> documentStructureInput() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("workspaceId", uuidProperty("DevCollab workspace identifier"));
        properties.put("documentId", uuidProperty("DevCollab document identifier"));
        properties.put("includeBlockContent", Map.of(
                "type", "boolean",
                "default", false
        ));
        properties.put("maxBlocks", Map.of(
                "type", "integer",
                "minimum", 1,
                "maximum", 500
        ));
        properties.put("maxContentCharacters", Map.of(
                "type", "integer",
                "minimum", 1,
                "maximum", 100000
        ));
        return objectSchema(properties, List.of("workspaceId", "documentId"));
    }

    static Map<String, Object> documentStructureOutput() {
        Map<String, Object> blockProperties = new LinkedHashMap<>();
        blockProperties.put("blockId", uuidProperty("Block identifier"));
        blockProperties.put("blockType", Map.of("type", "string"));
        blockProperties.put("sortOrder", Map.of("type", "integer"));
        blockProperties.put("version", Map.of("type", "integer"));
        blockProperties.put("plainText", Map.of("type", List.of("string", "null")));
        blockProperties.put("content", Map.of("type", List.of("string", "null")));
        blockProperties.put("contentTruncated", Map.of("type", "boolean"));
        Map<String, Object> block = objectSchema(
                blockProperties,
                List.of("blockId", "blockType", "sortOrder", "version")
        );
        return toolOutputSchema(
                Map.of(
                        "documentId", uuidProperty("Document identifier"),
                        "workspaceId", uuidProperty("Workspace identifier"),
                        "title", Map.of("type", "string"),
                        "documentType", Map.of("type", "string"),
                        "reviewStatus", Map.of("type", "string"),
                        "updatedAt", Map.of("type", "string", "format", "date-time"),
                        "blocks", Map.of("type", "array", "items", block),
                        "truncated", Map.of("type", "boolean"),
                        "omittedBlockCount", Map.of("type", "integer"),
                        "omittedCharacterCount", Map.of("type", "integer")
                ),
                List.of("documentId", "workspaceId", "title", "documentType", "reviewStatus", "updatedAt", "blocks", "truncated", "omittedBlockCount", "omittedCharacterCount")
        );
    }
    
    static Map<String, Object> bindingListInput() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("workspaceId", uuidProperty("DevCollab workspace identifier"));
        properties.put("repositoryId", uuidProperty("Registered repository identifier"));
        properties.put("filePath", Map.of(
                "type", "string",
                "minLength", 1,
                "maxLength", 2048
        ));
        return objectSchema(properties, List.of("workspaceId", "repositoryId", "filePath"));
    }

    static Map<String, Object> bindingListOutput() {
        Map<String, Object> binding = objectSchema(
                Map.of(
                        "bindingId", uuidProperty("Binding identifier"),
                        "pathPattern", Map.of("type", "string"),
                        "documentId", uuidProperty("Document identifier"),
                        "documentTitle", Map.of("type", List.of("string", "null")),
                        "blockId", uuidProperty("Block identifier", true)
                ),
                List.of("bindingId", "pathPattern", "documentId", "documentTitle")
        );
        return toolOutputSchema(
                Map.of(
                        "workspaceId", uuidProperty("Workspace identifier"),
                        "repositoryId", uuidProperty("Repository identifier"),
                        "filePath", Map.of("type", "string"),
                        "fileHasBindings", Map.of("type", "boolean"),
                        "bindings", Map.of("type", "array", "items", binding),
                        "truncated", Map.of("type", "boolean"),
                        "omittedBindingCount", Map.of("type", "integer")
                ),
                List.of("workspaceId", "repositoryId", "filePath", "fileHasBindings", "bindings", "truncated", "omittedBindingCount")
        );
    }

    private static Map<String, Object> toolOutputSchema(
            Map<String, Object> successProperties,
            List<String> successRequired
    ) {
        Map<String, Object> properties = new LinkedHashMap<>(successProperties);
        properties.put("error", objectSchema(
                Map.of(
                        "code", Map.of("type", "string"),
                        "message", Map.of("type", "string"),
                        "retryable", Map.of("type", "boolean"),
                        "details", Map.of("type", "object")
                ),
                List.of("code", "message", "retryable", "details")
        ));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("oneOf", List.of(
                Map.of("required", successRequired),
                Map.of("required", List.of("error"))
        ));
        schema.put("additionalProperties", false);
        return schema;
    }

    private static Map<String, Object> objectSchema(
            Map<String, Object> properties,
            List<String> required
    ) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private static Map<String, Object> uuidProperty(String description) {
        return uuidProperty(description, false);
    }

    private static Map<String, Object> uuidProperty(String description, boolean nullable) {
        if (nullable) {
            return Map.of(
                    "type", List.of("string", "null"),
                    "format", "uuid",
                    "description", description
            );
        }
        return Map.of(
                "type", "string",
                "format", "uuid",
                "description", description
        );
    }
}
