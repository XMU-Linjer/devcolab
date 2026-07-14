package com.devcollab.knowledgecore.search.api;

import com.devcollab.knowledgecore.search.domain.SearchHit;
import com.devcollab.knowledgecore.search.domain.SearchHighlightRange;
import com.devcollab.knowledgecore.search.domain.SearchHitType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SearchHitResponse(
        SearchHitType type,
        UUID documentId,
        String documentTitle,
        UUID blockId,
        String snippet,
        List<SearchHighlightRange> highlights,
        Instant updatedAt
) {
    public static SearchHitResponse from(SearchHit hit) {
        return new SearchHitResponse(
                hit.type(),
                hit.documentId(),
                hit.documentTitle(),
                hit.blockId(),
                hit.snippet(),
                hit.highlights(),
                hit.updatedAt()
        );
    }
}
