package com.devcollab.knowledgecore.document.api;

import com.devcollab.knowledgecore.document.application.DocumentCollaborationOperationPage;

import java.util.List;

public record DocumentCollaborationOperationPageResponse(
        long requestedAfterSequence,
        long latestDocumentSequence,
        boolean hasMore,
        List<DocumentCollaborationOperationResponse> operations
) {
    public static DocumentCollaborationOperationPageResponse from(
            DocumentCollaborationOperationPage page
    ) {
        return new DocumentCollaborationOperationPageResponse(
                page.requestedAfterSequence(),
                page.latestDocumentSequence(),
                page.hasMore(),
                page.operations().stream()
                        .map(DocumentCollaborationOperationResponse::from)
                        .toList()
        );
    }
}
