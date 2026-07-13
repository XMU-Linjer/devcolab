package com.devcollab.knowledgecore.search.api;

import com.devcollab.knowledgecore.search.domain.SearchHit;
import com.devcollab.knowledgecore.search.domain.SearchHitType;

import java.time.Instant;
import java.util.UUID;

public record SearchHitResponse(
        SearchHitType type,
        UUID documentId,
        String documentTitle,
        UUID blockId,
        String snippet,
        Instant updatedAt
) {
    public static SearchHitResponse from(SearchHit hit) {
        return new SearchHitResponse(
                hit.type(),
                hit.documentId(),
                hit.documentTitle(),
                hit.blockId(),
                hit.snippet(),
                hit.updatedAt()
        );
    }
}
