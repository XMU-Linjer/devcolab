package com.devcollab.knowledgecore.document.api;

import java.util.UUID;

public record MoveDocumentRequest(
        UUID parentDocumentId
) {
}
