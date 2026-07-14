package com.devcollab.knowledgecore.search.projection;

import com.devcollab.knowledgecore.search.domain.SearchHit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SearchIndexGateway {

    void ensureIndex();

    void upsert(SearchIndexEntry entry);

    void deleteByIndexId(String indexId);

    void deleteByDocumentId(UUID documentId);

    void updateDocumentTitle(
            UUID documentId,
            String documentTitle,
            Instant updatedAt
    );

    List<SearchHit> searchWorkspace(UUID workspaceId, String keyword, int limit);
}
