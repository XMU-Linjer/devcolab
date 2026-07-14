package com.devcollab.knowledgecore.search.projection;

import com.devcollab.knowledgecore.search.domain.SearchHit;
import com.devcollab.knowledgecore.search.domain.SearchHitType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(
        name = "devcollab.search.elasticsearch.enabled",
        havingValue = "true"
)
public class ElasticsearchSearchIndexGateway implements SearchIndexGateway {

    private static final int SNIPPET_LIMIT = 120;

    private final RestClient restClient;
    private final ElasticsearchSearchProperties properties;
    private final AtomicBoolean indexChecked = new AtomicBoolean(false);

    public ElasticsearchSearchIndexGateway(
            RestClient.Builder restClientBuilder,
            ElasticsearchSearchProperties properties
    ) {
        this.restClient = restClientBuilder
                .baseUrl(properties.url())
                .build();
        this.properties = properties;
    }

    @Override
    public void ensureIndex() {
        if (!indexChecked.compareAndSet(false, true)) {
            return;
        }

        try {
            restClient.put()
                    .uri("/{indexName}", properties.indexName())
                    .body(indexDefinition())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() != 400
                    || !exception.getResponseBodyAsString()
                    .contains("resource_already_exists_exception")) {
                indexChecked.set(false);
                throw exception;
            }
        }
    }

    @Override
    public void upsert(SearchIndexEntry entry) {
        restClient.put()
                .uri("/{indexName}/_doc/{id}", properties.indexName(), entry.id())
                .body(Map.of(
                        "workspaceId", entry.workspaceId().toString(),
                        "documentId", entry.documentId().toString(),
                        "documentTitle", entry.documentTitle(),
                        "hitType", entry.hitType().name(),
                        "blockId", entry.blockId() == null
                                ? ""
                                : entry.blockId().toString(),
                        "text", entry.text(),
                        "sortOrder", entry.sortOrder(),
                        "updatedAt", entry.updatedAt().toString()
                ))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void deleteByIndexId(String indexId) {
        restClient.delete()
                .uri("/{indexName}/_doc/{id}", properties.indexName(), indexId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                        (request, response) -> {
                            // Deleting an already-removed projection is idempotent.
                        })
                .toBodilessEntity();
    }

    @Override
    public void deleteByDocumentId(UUID documentId) {
        deleteByTerm("documentId", documentId.toString());
    }

    @Override
    public void updateDocumentTitle(
            UUID documentId,
            String documentTitle,
            Instant updatedAt
    ) {
        restClient.post()
                .uri("/{indexName}/_update_by_query", properties.indexName())
                .body(Map.of(
                        "script", Map.of(
                                "source", """
                                        ctx._source.documentTitle = params.title;
                                        ctx._source.updatedAt = params.updatedAt;
                                        """,
                                "lang", "painless",
                                "params", Map.of(
                                        "title", documentTitle,
                                        "updatedAt", updatedAt.toString()
                                )
                        ),
                        "query", Map.of(
                                "term", Map.of("documentId", documentId.toString())
                        )
                ))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<SearchHit> searchWorkspace(
            UUID workspaceId,
            String keyword,
            int limit
    ) {
        Map<String, Object> response = restClient.post()
                .uri("/{indexName}/_search", properties.indexName())
                .body(searchBody(workspaceId, keyword, limit))
                .retrieve()
                .body(Map.class);

        if (response == null) {
            return List.of();
        }

        Map<String, Object> hitsWrapper = (Map<String, Object>) response.get("hits");
        if (hitsWrapper == null) {
            return List.of();
        }

        List<Map<String, Object>> hits =
                (List<Map<String, Object>>) hitsWrapper.get("hits");
        if (hits == null) {
            return List.of();
        }

        return hits.stream()
                .map(hit -> (Map<String, Object>) hit.get("_source"))
                .map(source -> new SearchHit(
                        SearchHitType.valueOf((String) source.get("hitType")),
                        UUID.fromString((String) source.get("documentId")),
                        (String) source.get("documentTitle"),
                        blockId(source),
                        snippet((String) source.get("text"), keyword),
                        Instant.parse((String) source.get("updatedAt"))
                ))
                .toList();
    }

    private void deleteByTerm(String field, String value) {
        restClient.post()
                .uri("/{indexName}/_delete_by_query", properties.indexName())
                .body(Map.of(
                        "query", Map.of(
                                "term", Map.of(field, value)
                        )
                ))
                .retrieve()
                .toBodilessEntity();
    }

    private static Map<String, Object> indexDefinition() {
        return Map.of(
                "mappings", Map.of(
                        "properties", Map.of(
                                "workspaceId", Map.of("type", "keyword"),
                                "documentId", Map.of("type", "keyword"),
                                "documentTitle", Map.of("type", "text"),
                                "hitType", Map.of("type", "keyword"),
                                "blockId", Map.of("type", "keyword"),
                                "text", Map.of("type", "text"),
                                "sortOrder", Map.of("type", "integer"),
                                "updatedAt", Map.of("type", "date")
                        )
                )
        );
    }

    private static Map<String, Object> searchBody(
            UUID workspaceId,
            String keyword,
            int limit
    ) {
        return Map.of(
                "size", limit,
                "query", Map.of(
                        "bool", Map.of(
                                "filter", List.of(Map.of(
                                        "term",
                                        Map.of("workspaceId", workspaceId.toString())
                                )),
                                "must", List.of(Map.of(
                                        "multi_match",
                                        Map.of(
                                                "query", keyword,
                                                "fields", List.of(
                                                        "documentTitle^3",
                                                        "text"
                                                )
                                        )
                                ))
                        )
                ),
                "sort", List.of(
                        Map.of("_score", Map.of("order", "desc")),
                        Map.of("updatedAt", Map.of("order", "desc"))
                )
        );
    }

    private static UUID blockId(Map<String, Object> source) {
        String value = (String) source.get("blockId");
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }

    private static String snippet(String text, String keyword) {
        String normalizedText = text == null ? "" : text;
        if (normalizedText.length() <= SNIPPET_LIMIT) {
            return normalizedText;
        }

        String lowerText = normalizedText.toLowerCase();
        String lowerKeyword = keyword.toLowerCase();
        int hitIndex = lowerText.indexOf(lowerKeyword);
        if (hitIndex < 0) {
            return normalizedText.substring(0, SNIPPET_LIMIT) + "...";
        }

        int start = Math.max(0, hitIndex - 40);
        int end = Math.min(normalizedText.length(), start + SNIPPET_LIMIT);
        String prefix = start > 0 ? "..." : "";
        String suffix = end < normalizedText.length() ? "..." : "";
        return prefix + normalizedText.substring(start, end) + suffix;
    }
}
