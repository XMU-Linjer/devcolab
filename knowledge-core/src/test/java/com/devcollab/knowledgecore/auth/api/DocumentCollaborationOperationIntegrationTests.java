package com.devcollab.knowledgecore.auth.api;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentCollaborationOperationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void duplicateOperationReturnsOriginalResultWithoutApplyingTwice()
            throws Exception {
        Fixture fixture = fixture();
        UUID operationId = UUID.randomUUID();
        String body = operationBody(
                operationId,
                fixture.blockId(),
                "Saved once",
                0
        );

        apply(fixture, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.documentSequence").value(1))
                .andExpect(jsonPath("$.block.version").value(1));

        mockMvc.perform(post(
                        "/api/v1/documents/{documentId}/submit-review",
                        fixture.documentId()
                ).header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(fixture.token())
                ))
                .andExpect(status().isOk());

        apply(fixture, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DUPLICATE"))
                .andExpect(jsonPath("$.documentSequence").value(1))
                .andExpect(jsonPath("$.block.content.text")
                        .value("Saved once"))
                .andExpect(jsonPath("$.block.version").value(1));

        mockMvc.perform(get(
                        "/api/v1/documents/{documentId}/blocks",
                        fixture.documentId()
                ).header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(fixture.token())
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content.text").value("Saved once"))
                .andExpect(jsonPath("$[0].version").value(1));
    }

    @Test
    void acceptedOperationsReceiveMonotonicDocumentSequence()
            throws Exception {
        Fixture fixture = fixture();

        apply(fixture, operationBody(
                UUID.randomUUID(),
                fixture.blockId(),
                "First",
                0
        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentSequence").value(1));

        apply(fixture, operationBody(
                UUID.randomUUID(),
                fixture.blockId(),
                "Second",
                1
        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentSequence").value(2))
                .andExpect(jsonPath("$.block.version").value(2));
    }

    @Test
    void reusingOperationIdForDifferentRequestIsRejected()
            throws Exception {
        Fixture fixture = fixture();
        UUID operationId = UUID.randomUUID();

        apply(fixture, operationBody(
                operationId,
                fixture.blockId(),
                "Original request",
                0
        )).andExpect(status().isOk());

        apply(fixture, operationBody(
                operationId,
                fixture.blockId(),
                "Changed request",
                1
        ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("DOCUMENT_OPERATION_ID_REUSED"));
    }

    @Test
    void versionConflictDoesNotConsumeDocumentSequence()
            throws Exception {
        Fixture fixture = fixture();

        apply(fixture, operationBody(
                UUID.randomUUID(),
                fixture.blockId(),
                "First",
                0
        )).andExpect(status().isOk());

        apply(fixture, operationBody(
                UUID.randomUUID(),
                fixture.blockId(),
                "Stale",
                0
        ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("DOCUMENT_BLOCK_VERSION_CONFLICT"));

        apply(fixture, operationBody(
                UUID.randomUUID(),
                fixture.blockId(),
                "Second",
                1
        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentSequence").value(2));
    }

    private org.springframework.test.web.servlet.ResultActions apply(
            Fixture fixture,
            String body
    ) throws Exception {
        return mockMvc.perform(post(
                        "/api/v1/documents/{documentId}/collaboration-operations",
                        fixture.documentId()
                )
                .header(HttpHeaders.AUTHORIZATION, bearer(fixture.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private Fixture fixture() throws Exception {
        String token = registerAndGetAccessToken();
        String workspaceId = responseJson(mockMvc.perform(
                        post("/api/v1/workspaces")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Collaboration workspace\"}")
                )
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        String documentId = responseJson(mockMvc.perform(post(
                        "/api/v1/workspaces/{id}/documents",
                        workspaceId
                )
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Collaboration document\"}"))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        String blockId = responseJson(mockMvc.perform(post(
                        "/api/v1/documents/{id}/blocks",
                        documentId
                )
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PARAGRAPH","content":{"text":"Initial"}}
                                """))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        return new Fixture(token, documentId, blockId);
    }

    private String operationBody(
            UUID operationId,
            String blockId,
            String text,
            long expectedVersion
    ) throws Exception {
        return objectMapper.writeValueAsString(new OperationBody(
                operationId,
                UUID.fromString(blockId),
                "UPDATE_TEXT",
                expectedVersion,
                new ContentBody(text)
        ));
    }

    private String registerAndGetAccessToken() throws Exception {
        String username = "collab_" + UUID.randomUUID()
                .toString()
                .substring(0, 8);
        String body = objectMapper.writeValueAsString(
                new RegisterBody(username, "Collaboration Tester", "password123")
        );
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
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

    private record Fixture(String token, String documentId, String blockId) {
    }

    private record OperationBody(
            UUID clientOperationId,
            UUID blockId,
            String operationType,
            long expectedVersion,
            ContentBody content
    ) {
    }

    private record ContentBody(String text) {
    }

    private record RegisterBody(
            String username,
            String displayName,
            String password
    ) {
    }
}
