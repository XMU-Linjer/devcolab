package com.devcollab.knowledgecore.document.domain;

import java.util.List;

public record DocumentCollaborationOperationPayload(
        DocumentBlock block,
        List<DocumentBlock> blocks
) {
    public DocumentCollaborationOperationPayload {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }

    public static DocumentCollaborationOperationPayload single(
            DocumentBlock block
    ) {
        return new DocumentCollaborationOperationPayload(block, List.of());
    }

    public static DocumentCollaborationOperationPayload ordered(
            DocumentBlock block,
            List<DocumentBlock> blocks
    ) {
        return new DocumentCollaborationOperationPayload(block, blocks);
    }
}
