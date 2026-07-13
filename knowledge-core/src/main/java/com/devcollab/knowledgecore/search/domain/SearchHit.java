package com.devcollab.knowledgecore.search.domain;

import java.time.Instant;
import java.util.UUID;

public record SearchHit(
        SearchHitType type,
        UUID documentId,
        String documentTitle,
        UUID blockId,
        String snippet,
        Instant updatedAt
) {
}
