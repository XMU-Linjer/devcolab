package com.devcollab.worker.search.projection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(
        name = "devcollab.search.elasticsearch.enabled",
        havingValue = "true"
)
public class ElasticsearchSearchIndexGateway {

    private static final Logger log =
            LoggerFactory.getLogger(ElasticsearchSearchIndexGateway.class);

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

    public void deleteByDocumentId(UUID documentId) {
        restClient.post()
                .uri("/{indexName}/_delete_by_query", properties.indexName())
                .body(Map.of(
                        "query", Map.of(
                                "term", Map.of("documentId", documentId.toString())
                        )
                ))
                .retrieve()
                .toBodilessEntity();
    }

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
}