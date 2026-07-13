package com.devcollab.knowledgecore;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PostgreSqlKnowledgeCoreIT {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldRunBlockEditFlowOnPostgreSql() throws Exception {
        String token = registerAndGetAccessToken();
        String workspaceId = createWorkspace(token).get("id").asText();
        String documentId = createDocument(token, workspaceId)
                .get("id")
                .asText();
        JsonNode block = createBlock(token, documentId, "Postgres content");
        String blockId = block.get("id").asText();

        mockMvc.perform(patch(
                        "/api/v1/documents/{documentId}/blocks/{blockId}",
                        documentId,
                        blockId
                )
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBlockBody("Postgres updated", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.text")
                        .value("Postgres updated"))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(patch(
                        "/api/v1/documents/{documentId}/blocks/{blockId}",
                        documentId,
                        blockId
                )
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBlockBody("Postgres stale", 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("DOCUMENT_BLOCK_VERSION_CONFLICT"));

        mockMvc.perform(get("/api/v1/workspaces/{id}/search", workspaceId)
                        .queryParam("keyword", "Postgres")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].documentId")
                        .value(hasItem(documentId)))
                .andExpect(jsonPath("$[*].snippet")
                        .value(hasItem("Postgres updated")));
    }

    private JsonNode createWorkspace(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/workspaces")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Postgres workspace\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return responseJson(result);
    }

    private JsonNode createDocument(String token, String workspaceId)
            throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/v1/workspaces/{id}/documents", workspaceId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\":\"Postgres document\"}")
                )
                .andExpect(status().isCreated())
                .andReturn();
        return responseJson(result);
    }

    private JsonNode createBlock(String token, String documentId, String text)
            throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/v1/documents/{id}/blocks", documentId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(blockBody("PARAGRAPH", text))
                )
                .andExpect(status().isCreated())
                .andReturn();
        return responseJson(result);
    }

    private String registerAndGetAccessToken() throws Exception {
        String username = "pg_" + UUID.randomUUID()
                .toString()
                .substring(0, 8);
        String requestBody = objectMapper.writeValueAsString(
                new RegisterBody(username, "Postgres Tester", "password123")
        );
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();
        return responseJson(result).get("accessToken").asText();
    }

    private String blockBody(String type, String text) throws Exception {
        return objectMapper.writeValueAsString(
                new BlockBody(type, new BlockContentBody(text))
        );
    }

    private String updateBlockBody(String text, long expectedVersion)
            throws Exception {
        return objectMapper.writeValueAsString(
                new UpdateBlockBody(
                        new BlockContentBody(text),
                        expectedVersion
                )
        );
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

    private record BlockBody(String type, BlockContentBody content) {
    }

    private record BlockContentBody(String text) {
    }

    private record UpdateBlockBody(
            BlockContentBody content,
            long expectedVersion
    ) {
    }
}
