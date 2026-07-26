package com.devcollab.knowledgecore.document.api;

import com.devcollab.knowledgecore.document.application.DocumentCandidateItem;

import java.util.List;
import java.util.UUID;

public record DocumentCandidateItemResponse(
        UUID documentId,
        String title,
        int score,
        List<DocumentCandidateMatchReasonResponse> matchReasons,
        List<UUID> matchedBlockIds,
        int existingBindingCount
) {
    static DocumentCandidateItemResponse from(DocumentCandidateItem item) {
        return new DocumentCandidateItemResponse(
                item.documentId(), item.title(), item.score(),
                item.matchReasons().stream().map(DocumentCandidateMatchReasonResponse::from).toList(),
                item.matchedBlockIds(), item.existingBindingCount()
        );
    }
}
