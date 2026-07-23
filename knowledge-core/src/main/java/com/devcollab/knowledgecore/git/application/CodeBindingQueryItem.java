package com.devcollab.knowledgecore.git.application;

import java.util.UUID;

public record CodeBindingQueryItem(
        UUID bindingId,
        UUID documentId,
        UUID blockId,
        String pathPattern,
        String documentTitle
) {
}
