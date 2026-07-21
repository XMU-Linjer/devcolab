package com.devcollab.knowledgecore.git.application;

import java.util.List;
import java.util.UUID;

public record AffectedCodeDocument(
        UUID bindingId,
        UUID documentId,
        UUID blockId,
        String pathPattern,
        List<String> matchedPaths
) {
}
