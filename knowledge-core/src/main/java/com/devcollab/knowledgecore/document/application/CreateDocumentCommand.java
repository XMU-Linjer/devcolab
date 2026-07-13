package com.devcollab.knowledgecore.document.application;

import java.util.UUID;

public record CreateDocumentCommand(
        UUID parentDocumentId,
        String title
) {
}
