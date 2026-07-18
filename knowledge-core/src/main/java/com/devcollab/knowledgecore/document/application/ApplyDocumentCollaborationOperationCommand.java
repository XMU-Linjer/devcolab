package com.devcollab.knowledgecore.document.application;

import com.devcollab.knowledgecore.document.domain.DocumentBlockType;

import java.util.UUID;

public record ApplyDocumentCollaborationOperationCommand(
        UUID clientOperationId,
        UUID blockId,
        String operationType,
        Long expectedVersion,
        DocumentBlockType blockType,
        Integer targetIndex,
        String text
) {
}
