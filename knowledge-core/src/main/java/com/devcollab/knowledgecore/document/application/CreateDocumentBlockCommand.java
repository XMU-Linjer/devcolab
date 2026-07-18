package com.devcollab.knowledgecore.document.application;

import com.devcollab.knowledgecore.document.domain.DocumentBlockType;
import com.fasterxml.jackson.databind.JsonNode;

public record CreateDocumentBlockCommand(
        DocumentBlockType type,
        String text,
        Integer contentSchemaVersion,
        JsonNode contentDocument
) {
}
