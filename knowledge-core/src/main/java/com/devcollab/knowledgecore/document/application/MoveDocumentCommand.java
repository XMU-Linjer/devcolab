package com.devcollab.knowledgecore.document.application;

import java.util.UUID;

public record MoveDocumentCommand(UUID parentDocumentId) {
}
