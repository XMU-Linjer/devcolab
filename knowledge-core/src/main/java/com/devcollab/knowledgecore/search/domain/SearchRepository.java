package com.devcollab.knowledgecore.search.domain;

import java.util.List;
import java.util.UUID;

public interface SearchRepository {

    List<SearchHit> searchWorkspace(
            UUID workspaceId,
            String keyword,
            int limit
    );
}
