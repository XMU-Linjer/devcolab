package com.devcollab.knowledgecore.document.application;

import com.devcollab.knowledgecore.document.domain.DocumentBlockType;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record ApplyDocumentCollaborationOperationCommand(
        UUID clientOperationId,
        UUID blockId,
        String operationType,
        Long expectedVersion,
        DocumentBlockType blockType,
        Integer targetIndex,
        String text,
        Integer contentSchemaVersion,
        JsonNode contentDocument
) {
}
