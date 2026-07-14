package com.devcollab.knowledgecore.search.api;

import com.devcollab.knowledgecore.common.outbox.application.OutboxRelayResult;
import com.devcollab.knowledgecore.common.outbox.application.OutboxRelayService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "devcollab.search.engine=elasticsearch",
        "devcollab.search.elasticsearch.enabled=true",
        "devcollab.search.elasticsearch.url=http://localhost:9200",
        "devcollab.search.elasticsearch.index-name=devcollab-search-it"
})
@AutoConfigureMockMvc
@EnabledIfSystemProperty(
        named = "devcollab.elasticsearch.it",
        matches = "true"
)
class ElasticsearchSearchProjectionIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OutboxRelayService outboxRelayService;

    private final RestClient elasticsearch = RestClient.builder()
            .baseUrl("http://localhost:9200")
            .build();

    @Test
    void shouldSearchDocumentAndBlockThroughElasticsearchProjection()
            throws Exception {
        String token = registerAndGetAccessToken();
        String workspaceId = createWorkspace(token, "ES integration")
                .get("id")
                .asText();
        String documentId = createDocument(
                token,
                workspaceId,
                "Order API Contract"
        ).get("id").asText();
        createBlock(
                token,
                documentId,
                "POST /api/orders requires idempotency key"
        );

        OutboxRelayResult relayResult = outboxRelayService.relayPendingEvents();
        refreshIndex();

        MvcResult result = mockMvc.perform(
                        get("/api/v1/workspaces/{id}/search", workspaceId)
                                .queryParam("keyword", "api")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                )
                .andExpect(status().isOk())
                .andReturn();

        JsonNode hits = responseJson(result);
        assertThat(relayResult.published()).isGreaterThanOrEqualTo(2);
        assertThat(countByType(hits, "DOCUMENT_TITLE")).isEqualTo(1);
        assertThat(countByType(hits, "BLOCK_CONTENT")).isEqualTo(1);

        System.out.printf(
                "SEARCH_INTEGRATION engine=elasticsearch scenario=document-block-projection keyword=api hits=%d titleHits=%d contentHits=%d publishedEvents=%d failedEvents=%d%n",
                hits.size(),
                countByType(hits, "DOCUMENT_TITLE"),
                countByType(hits, "BLOCK_CONTENT"),
                relayResult.published(),
                relayResult.failed()
        );
    }

    @Test
    void shouldProvideRepeatableElasticsearchSearchBaseline()
            throws Exception {
        String token = registerAndGetAccessToken();
        String workspaceId = createWorkspace(token, "ES search baseline")
                .get("id")
                .asText();
        seedBaselineDocuments(token, workspaceId);

        OutboxRelayResult relayResult = outboxRelayService.relayPendingEvents();
        refreshIndex();

        assertThat(relayResult.published()).isGreaterThanOrEqualTo(9);
        assertThat(relayResult.failed()).isZero();

        assertBaselineScenario(
                token,
                workspaceId,
                "title-and-content-api",
                "api",
                3,
                1,
                2
        );
        assertBaselineScenario(
                token,
                workspaceId,
                "content-idempotency",
                "idempotency",
                1,
                0,
                1
        );
        assertBaselineScenario(
                token,
                workspaceId,
                "content-csrf",
                "csrf",
                1,
                0,
                1
        );
        assertBaselineScenario(
                token,
                workspaceId,
                "title-and-content-agent",
                "agent",
                2,
                1,
                1
        );
        assertBaselineScenario(
                token,
                workspaceId,
                "no-hit",
                "not-exists-keyword",
                0,
                0,
                0
        );
    }

    private void assertBaselineScenario(
            String token,
            String workspaceId,
            String scenarioName,
            String keyword,
            int expectedTotalHits,
            int expectedTitleHits,
            int expectedContentHits
    ) throws Exception {
        long startedAt = System.nanoTime();
        MvcResult result = mockMvc.perform(
                        get("/api/v1/workspaces/{id}/search", workspaceId)
                                .queryParam("keyword", keyword)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                )
                .andExpect(status().isOk())
                .andReturn();
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;

        JsonNode hits = responseJson(result);
        int titleHits = countByType(hits, "DOCUMENT_TITLE");
        int contentHits = countByType(hits, "BLOCK_CONTENT");

        assertThat(hits).hasSize(expectedTotalHits);
        assertThat(titleHits).isEqualTo(expectedTitleHits);
        assertThat(contentHits).isEqualTo(expectedContentHits);

        System.out.printf(
                "SEARCH_BASELINE engine=elasticsearch scenario=%s keyword=%s hits=%d titleHits=%d contentHits=%d durationMs=%d%n",
                scenarioName,
                keyword,
                hits.size(),
                titleHits,
                contentHits,
                durationMs
        );
    }

    private void seedBaselineDocuments(String token, String workspaceId)
            throws Exception {
        String orderApiDocumentId = createDocument(
                token,
                workspaceId,
                "Order API Contract"
        ).get("id").asText();
        createBlock(
                token,
                orderApiDocumentId,
                "POST /api/orders requires idempotency key"
        );
        createBlock(
                token,
                orderApiDocumentId,
                "Order API response returns orderId and status"
        );

        String authDocumentId = createDocument(
                token,
                workspaceId,
                "Login Session Security"
        ).get("id").asText();
        createBlock(
                token,
                authDocumentId,
                "Refresh token uses HttpOnly cookie and CSRF header"
        );

        String agentDocumentId = createDocument(
                token,
                workspaceId,
                "Agent Review Evidence"
        ).get("id").asText();
        createBlock(
                token,
                agentDocumentId,
                "Agent review reads requirement docs and code changes"
        );

        String frontendDocumentId = createDocument(
                token,
                workspaceId,
                "Frontend Workspace Search"
        ).get("id").asText();
        createBlock(
                token,
                frontendDocumentId,
                "Vue search panel opens matched document"
        );
    }

    private void refreshIndex() {
        elasticsearch.post()
                .uri("/devcollab-search-it/_refresh")
                .retrieve()
                .toBodilessEntity();
    }

    private int countByType(JsonNode hits, String type) {
        int count = 0;
        for (JsonNode hit : hits) {
            if (type.equals(hit.get("type").asText())) {
                count++;
            }
        }
        return count;
    }

    private JsonNode createWorkspace(String token, String name)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/workspaces")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new WorkspaceBody(name)
                        )))
                .andExpect(status().isCreated())
                .andReturn();
        return responseJson(result);
    }

    private JsonNode createDocument(
            String token,
            String workspaceId,
            String title
    ) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/v1/workspaces/{id}/documents", workspaceId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        new DocumentBody(null, title)
                                ))
                )
                .andExpect(status().isCreated())
                .andReturn();
        return responseJson(result);
    }

    private void createBlock(
            String token,
            String documentId,
            String text
    ) throws Exception {
        mockMvc.perform(
                        post("/api/v1/documents/{id}/blocks", documentId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        new BlockBody(
                                                "PARAGRAPH",
                                                new BlockContentBody(text)
                                        )
                                ))
                )
                .andExpect(status().isCreated());
    }

    private String registerAndGetAccessToken() throws Exception {
        String username = "es_it_" + UUID.randomUUID()
                .toString()
                .substring(0, 8);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterBody(
                                        username,
                                        "ES Integration Tester",
                                        "password123"
                                )
                        )))
                .andExpect(status().isCreated())
                .andReturn();
        return responseJson(result).get("accessToken").asText();
    }

    private JsonNode responseJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record RegisterBody(
            String username,
            String displayName,
            String password
    ) {
    }

    private record WorkspaceBody(String name) {
    }

    private record DocumentBody(
            String parentDocumentId,
            String title
    ) {
    }

    private record BlockBody(
            String type,
            BlockContentBody content
    ) {
    }

    private record BlockContentBody(String text) {
    }
}
