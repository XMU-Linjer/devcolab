package com.devcollab.knowledgecore.search.application;

import com.devcollab.knowledgecore.search.domain.SearchHit;
import com.devcollab.knowledgecore.search.domain.SearchRepository;
import com.devcollab.knowledgecore.search.domain.SearchScope;
import com.devcollab.knowledgecore.workspace.application.WorkspaceApplicationService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class SearchApplicationService {

    private static final int DEFAULT_LIMIT = 20;

    private final SearchRepository searchRepository;
    private final WorkspaceApplicationService workspaceService;

    public SearchApplicationService(
            SearchRepository searchRepository,
            WorkspaceApplicationService workspaceService
    ) {
        this.searchRepository = searchRepository;
        this.workspaceService = workspaceService;
    }

    public List<SearchHit> searchWorkspace(
            UUID workspaceId,
            UUID currentUserId,
            String keyword,
            SearchScope scope
    ) {
        workspaceService.requireMembership(workspaceId, currentUserId);

        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (normalizedKeyword.isBlank()) {
            return List.of();
        }

        return searchRepository.searchWorkspace(
                workspaceId,
                normalizedKeyword,
                scope,
                DEFAULT_LIMIT
        );
    }
}
