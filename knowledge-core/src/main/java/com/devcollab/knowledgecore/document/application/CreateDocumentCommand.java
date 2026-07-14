package com.devcollab.knowledgecore.document.application;

import com.devcollab.knowledgecore.document.domain.DocumentType;

import java.util.UUID;

public record CreateDocumentCommand(
        UUID parentDocumentId,
        String title,
        DocumentType documentType
) {
}
