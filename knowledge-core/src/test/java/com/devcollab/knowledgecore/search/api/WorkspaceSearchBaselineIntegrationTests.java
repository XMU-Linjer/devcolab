package com.devcollab.knowledgecore.search.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WorkspaceSearchBaselineIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldProvideRepeatablePostgreSqlSearchBaseline()
            throws Exception {
        String token = registerAndGetAccessToken();
        String workspaceId = createWorkspace(token, "Search baseline")
                .get("id")
                .asText();
        seedBaselineDocuments(token, workspaceId);

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
                "SEARCH_BASELINE engine=postgres-mvp scenario=%s keyword=%s hits=%d titleHits=%d contentHits=%d durationMs=%d%n",
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
        String username = "baseline_" + UUID.randomUUID()
                .toString()
                .substring(0, 8);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterBody(
                                        username,
                                        "Baseline Tester",
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
