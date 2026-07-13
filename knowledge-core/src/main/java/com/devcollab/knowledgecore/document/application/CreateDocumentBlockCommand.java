package com.devcollab.knowledgecore.document.application;

import com.devcollab.knowledgecore.document.domain.DocumentBlockType;

public record CreateDocumentBlockCommand(
        DocumentBlockType type,
        String text
) {
}
