package com.devcollab.knowledgecore.document.application;

import com.devcollab.knowledgecore.document.domain.DocumentBlock;

import java.util.UUID;
import java.util.List;

public record DocumentCollaborationOperationResult(
        UUID clientOperationId,
        UUID blockId,
        String operationType,
        String status,
        long documentSequence,
        DocumentBlock block,
        List<DocumentBlock> blocks
) {
}
