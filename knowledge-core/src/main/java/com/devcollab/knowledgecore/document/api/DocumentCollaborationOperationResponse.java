package com.devcollab.knowledgecore.document.api;

import com.devcollab.knowledgecore.document.application.DocumentCollaborationOperationResult;

import java.util.UUID;

public record DocumentCollaborationOperationResponse(
        UUID clientOperationId,
        UUID blockId,
        String operationType,
        String status,
        long documentSequence,
        DocumentBlockResponse block
) {
    public static DocumentCollaborationOperationResponse from(
            DocumentCollaborationOperationResult result
    ) {
        return new DocumentCollaborationOperationResponse(
                result.clientOperationId(),
                result.blockId(),
                result.operationType(),
                result.status(),
                result.documentSequence(),
                DocumentBlockResponse.from(result.block())
        );
    }
}
