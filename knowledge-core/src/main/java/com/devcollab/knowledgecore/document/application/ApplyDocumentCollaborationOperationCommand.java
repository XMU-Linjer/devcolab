package com.devcollab.knowledgecore.document.application;

import java.util.UUID;

public record ApplyDocumentCollaborationOperationCommand(
        UUID clientOperationId,
        UUID blockId,
        String operationType,
        long expectedVersion,
        String text
) {
}
