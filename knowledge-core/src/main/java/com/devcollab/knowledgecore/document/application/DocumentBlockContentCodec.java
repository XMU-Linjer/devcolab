package com.devcollab.knowledgecore.document.application;

import com.devcollab.knowledgecore.document.domain.DocumentBlock;
import com.devcollab.knowledgecore.document.domain.DocumentBlockType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;

@Component
public class DocumentBlockContentCodec {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int MAX_TEXT_LENGTH = 20_000;
    public static final int MAX_JSON_BYTES = 64 * 1024;
    public static final int MAX_NODE_COUNT = 512;
    public static final int MAX_DEPTH = 8;

    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "type", "content", "text"
    );
    private static final Set<String> ALLOWED_NODE_TYPES = Set.of(
            "doc", "paragraph", "text", "hardBreak"
    );

    private final ObjectMapper objectMapper;

    public DocumentBlockContentCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public NormalizedContent normalize(
            DocumentBlockType blockType,
            String legacyText,
            Integer schemaVersion,
            JsonNode document
    ) {
        if (document == null || document.isNull()) {
            String text = normalizeText(legacyText);
            JsonNode synthesized = textDocument(text);
            return new NormalizedContent(
                    text,
                    CURRENT_SCHEMA_VERSION,
                    write(synthesized),
                    synthesized
            );
        }
        int requestedVersion = schemaVersion == null
                ? CURRENT_SCHEMA_VERSION
                : schemaVersion;
        if (requestedVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported Block content schema version: " + requestedVersion
            );
        }
        String encoded = write(document);
        if (encoded.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES) {
            throw new IllegalArgumentException(
                    "Block structured content exceeds 65536 bytes"
            );
        }
        Counter counter = new Counter();
        validateNode(document, 1, counter, true);
        String text = normalizeText(extractDocumentText(document));
        return new NormalizedContent(
                text,
                requestedVersion,
                encoded,
                document.deepCopy()
        );
    }

    public JsonNode document(DocumentBlock block) {
        if (block.contentJson() == null || block.contentJson().isBlank()) {
            return textDocument(block.text());
        }
        try {
            return objectMapper.readTree(block.contentJson());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Stored Block structured content is invalid",
                    exception
            );
        }
    }

    public int schemaVersion(DocumentBlock block) {
        return block.contentSchemaVersion() > 0
                ? block.contentSchemaVersion()
                : CURRENT_SCHEMA_VERSION;
    }

    private void validateNode(
            JsonNode node,
            int depth,
            Counter counter,
            boolean root
    ) {
        if (!node.isObject()) {
            throw new IllegalArgumentException("Every Block content node must be an object");
        }
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("Block content nesting exceeds 8 levels");
        }
        counter.value++;
        if (counter.value > MAX_NODE_COUNT) {
            throw new IllegalArgumentException("Block content exceeds 512 nodes");
        }
        Iterator<String> fieldNames = node.fieldNames();
        while (fieldNames.hasNext()) {
            String field = fieldNames.next();
            if (!ALLOWED_FIELDS.contains(field)) {
                throw new IllegalArgumentException(
                        "Unsupported Block content field: " + field
                );
            }
        }
        String type = requiredText(node, "type");
        if (!ALLOWED_NODE_TYPES.contains(type)) {
            throw new IllegalArgumentException(
                    "Unsupported Block content node type: " + type
            );
        }
        if (root && !"doc".equals(type)) {
            throw new IllegalArgumentException("Block content root type must be doc");
        }
        if (!root && "doc".equals(type)) {
            throw new IllegalArgumentException("Nested doc nodes are not allowed");
        }
        JsonNode content = node.get("content");
        if ("text".equals(type)) {
            if (content != null) {
                throw new IllegalArgumentException("Text nodes cannot contain child nodes");
            }
            requiredText(node, "text");
            return;
        }
        if (node.has("text")) {
            throw new IllegalArgumentException(
                    "Only text nodes may contain the text field"
            );
        }
        if ("hardBreak".equals(type)) {
            if (content != null) {
                throw new IllegalArgumentException("hardBreak cannot contain child nodes");
            }
            return;
        }
        if (content == null && "paragraph".equals(type)) {
            return;
        }
        if (content == null && "paragraph".equals(type)) {
            return;
        }
        if (content == null || !content.isArray()) {
            throw new IllegalArgumentException(type + " content must be an array");
        }
        for (JsonNode child : content) {
            String childType = child.path("type").asText();
            if (root && !"paragraph".equals(childType)) {
                throw new IllegalArgumentException(
                        "doc may contain paragraph nodes only"
                );
            }
            if ("paragraph".equals(type)
                    && !Set.of("text", "hardBreak").contains(childType)) {
                throw new IllegalArgumentException(
                        "paragraph may contain text or hardBreak nodes only"
                );
            }
            validateNode(child, depth + 1, counter, false);
        }
    }

    private String extractDocumentText(JsonNode document) {
        StringBuilder result = new StringBuilder();
        ArrayNode paragraphs = (ArrayNode) document.get("content");
        for (int index = 0; index < paragraphs.size(); index++) {
            if (index > 0) {
                result.append('\n');
            }
            appendInlineText(paragraphs.get(index).get("content"), result);
        }
        return result.toString();
    }

    private void appendInlineText(JsonNode content, StringBuilder result) {
        if (content == null || !content.isArray()) {
            return;
        }
        for (JsonNode child : content) {
            if ("text".equals(child.path("type").asText())) {
                result.append(child.path("text").asText());
            } else if ("hardBreak".equals(child.path("type").asText())) {
                result.append('\n');
            }
        }
    }

    private JsonNode textDocument(String value) {
        String text = value == null ? "" : value;
        ObjectNode root = objectMapper.createObjectNode().put("type", "doc");
        ArrayNode paragraphs = root.putArray("content");
        for (String line : text.split("\\R", -1)) {
            ObjectNode paragraph = paragraphs.addObject().put("type", "paragraph");
            ArrayNode content = paragraph.putArray("content");
            if (!line.isEmpty()) {
                content.addObject().put("type", "text").put("text", line);
            }
        }
        return root;
    }

    private String normalizeText(String text) {
        if (text == null) {
            throw new IllegalArgumentException(
                    "Either content.text or content.document is required"
            );
        }
        String normalized = text.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Block content must not be blank");
        }
        if (normalized.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(
                    "Block content must not exceed 20000 characters"
            );
        }
        return normalized;
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        return value.asText();
    }

    private String write(JsonNode document) {
        try {
            return objectMapper.writeValueAsString(document);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Block structured content is not valid JSON",
                    exception
            );
        }
    }

    public record NormalizedContent(
            String text,
            int schemaVersion,
            String documentJson,
            JsonNode document
    ) {
    }

    private static final class Counter {
        private int value;
    }
}
