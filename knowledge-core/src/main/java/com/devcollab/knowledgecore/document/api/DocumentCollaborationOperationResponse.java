package com.devcollab.knowledgecore.document.api;

import com.devcollab.knowledgecore.document.application.DocumentCollaborationOperationResult;
import com.devcollab.knowledgecore.document.application.DocumentBlockContentCodec;

import java.util.UUID;
import java.util.List;

public record DocumentCollaborationOperationResponse(
        UUID clientOperationId,
        UUID blockId,
        String operationType,
        String status,
        long documentSequence,
        UUID operatorUserId,
        DocumentBlockResponse block,
        List<DocumentBlockResponse> blocks
) {
    public static DocumentCollaborationOperationResponse from(
            DocumentCollaborationOperationResult result,
            DocumentBlockContentCodec contentCodec
    ) {
        return new DocumentCollaborationOperationResponse(
                result.clientOperationId(),
                result.blockId(),
                result.operationType(),
                result.status(),
                result.documentSequence(),
                result.operatorUserId(),
                result.block() == null
                        ? null
                        : DocumentBlockResponse.from(result.block(), contentCodec),
                result.blocks().stream()
                        .map(block -> DocumentBlockResponse.from(block, contentCodec))
                        .toList()
        );
    }
}
