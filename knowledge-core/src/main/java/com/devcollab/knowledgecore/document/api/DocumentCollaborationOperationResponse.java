package com.devcollab.knowledgecore.document.api;

import com.devcollab.knowledgecore.document.application.DocumentCollaborationOperationResult;

import java.util.UUID;
import java.util.List;

public record DocumentCollaborationOperationResponse(
        UUID clientOperationId,
        UUID blockId,
        String operationType,
        String status,
        long documentSequence,
        DocumentBlockResponse block,
        List<DocumentBlockResponse> blocks
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
                result.block() == null
                        ? null
                        : DocumentBlockResponse.from(result.block()),
                result.blocks().stream()
                        .map(DocumentBlockResponse::from)
                        .toList()
        );
    }
}
