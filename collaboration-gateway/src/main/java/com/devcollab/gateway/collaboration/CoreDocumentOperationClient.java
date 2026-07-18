package com.devcollab.gateway.collaboration;

import com.devcollab.gateway.collaboration.CollaborationMessages.CoreBlockResponse;

import java.util.List;
import java.util.UUID;

public interface CoreDocumentOperationClient {

    int MAX_CATCH_UP_PAGE_SIZE = 200;

    CoreDocumentOperationResult apply(
            UUID documentId,
            UUID blockId,
            UUID clientOperationId,
            String accessToken,
            String operationType,
            String text,
            Long expectedVersion,
            String blockType,
            Integer targetIndex
    );

    CollaborationMessages.DocumentOperationCatchUp listAfter(
            UUID documentId,
            String accessToken,
            long afterSequence,
            int limit
    );

    record CoreDocumentOperationResult(
            String status,
            Long documentSequence,
            CoreBlockResponse block,
            List<CoreBlockResponse> blocks,
            String message
    ) {
        public static CoreDocumentOperationResult conflict(String message) {
            return new CoreDocumentOperationResult(
                    "CONFLICT", null, null, List.of(), message
            );
        }

        public static CoreDocumentOperationResult rejected(String message) {
            return new CoreDocumentOperationResult(
                    "REJECTED", null, null, List.of(), message
            );
        }
    }
}
