package com.devcollab.knowledgecore.git.api;

import com.devcollab.knowledgecore.git.application.AffectedCodeDocument;

import java.util.List;
import java.util.UUID;

public record AffectedCodeDocumentResponse(
        UUID bindingId,
        UUID documentId,
        UUID blockId,
        String pathPattern,
        List<String> matchedPaths
) {
    public static AffectedCodeDocumentResponse from(AffectedCodeDocument affected) {
        return new AffectedCodeDocumentResponse(
                affected.bindingId(), affected.documentId(), affected.blockId(),
                affected.pathPattern(), affected.matchedPaths()
        );
    }
}
