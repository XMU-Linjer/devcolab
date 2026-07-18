package com.devcollab.knowledgecore.document.api;

import com.devcollab.knowledgecore.document.application.DocumentCollaborationOperationPage;
import com.devcollab.knowledgecore.document.application.DocumentBlockContentCodec;

import java.util.List;

public record DocumentCollaborationOperationPageResponse(
        long requestedAfterSequence,
        long latestDocumentSequence,
        boolean hasMore,
        List<DocumentCollaborationOperationResponse> operations
) {
    public static DocumentCollaborationOperationPageResponse from(
            DocumentCollaborationOperationPage page,
            DocumentBlockContentCodec contentCodec
    ) {
        return new DocumentCollaborationOperationPageResponse(
                page.requestedAfterSequence(),
                page.latestDocumentSequence(),
                page.hasMore(),
                page.operations().stream()
                        .map(operation -> DocumentCollaborationOperationResponse.from(
                                operation,
                                contentCodec
                        ))
                        .toList()
        );
    }
}
