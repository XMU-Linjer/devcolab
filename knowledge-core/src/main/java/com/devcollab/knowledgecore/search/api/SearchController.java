package com.devcollab.knowledgecore.search.api;

import com.devcollab.knowledgecore.search.application.SearchApplicationService;
import com.devcollab.knowledgecore.search.domain.SearchScope;
import com.devcollab.knowledgecore.security.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class SearchController {

    private final SearchApplicationService searchService;

    public SearchController(SearchApplicationService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/search")
    public List<SearchHitResponse> searchWorkspace(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam String keyword,
            @RequestParam(required = false) String scope
    ) {
        return searchService.searchWorkspace(
                        workspaceId,
                        currentUser.userId(),
                        keyword,
                        SearchScope.from(scope)
                )
                .stream()
                .map(SearchHitResponse::from)
                .toList();
    }
}
