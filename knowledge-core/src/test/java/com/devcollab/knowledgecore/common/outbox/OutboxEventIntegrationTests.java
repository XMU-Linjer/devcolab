package com.devcollab.knowledgecore.common.outbox;

import com.devcollab.knowledgecore.common.outbox.application.OutboxRelayResult;
import com.devcollab.knowledgecore.common.outbox.application.OutboxRelayService;
import com.devcollab.knowledgecore.common.outbox.domain.OutboxEvent;
import com.devcollab.knowledgecore.common.outbox.domain.OutboxEventRepository;
import com.devcollab.knowledgecore.common.outbox.domain.OutboxEventStatus;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OutboxEventIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxRelayService outboxRelayService;

    @Test
    void shouldWriteDocumentCreatedEventWithBusinessData()
            throws Exception {
        String token = registerAndGetAccessToken();
        String workspaceId = createWorkspace(token).get("id").asText();

        int before = outboxEventRepository.findAll().size();
        JsonNode document = createDocument(token, workspaceId, "Outbox doc");

        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertThat(events).hasSize(before + 1);
        OutboxEvent event = events.get(events.size() - 1);
        assertThat(event.aggregateType()).isEqualTo("DOCUMENT");
        assertThat(event.aggregateId().toString())
                .isEqualTo(document.get("id").asText());
        assertThat(event.eventType()).isEqualTo("DOCUMENT_CREATED");
        assertThat(event.status()).isEqualTo(OutboxEventStatus.PENDING);

        JsonNode payload = objectMapper.readTree(event.payload());
        assertThat(payload.get("workspaceId").asText()).isEqualTo(workspaceId);
        assertThat(payload.get("documentId").asText())
                .isEqualTo(document.get("id").asText());
        assertThat(payload.get("title").asText()).isEqualTo("Outbox doc");
    }

    @Test
    void shouldRelayPendingEventsToPublishedStatus() throws Exception {
        String token = registerAndGetAccessToken();
        String workspaceId = createWorkspace(token).get("id").asText();
        int before = outboxEventRepository.findAll().size();
        createDocument(token, workspaceId, "Relay doc");

        OutboxEvent pending = outboxEventRepository.findAll().get(before);
        assertThat(pending.status()).isEqualTo(OutboxEventStatus.PENDING);

        OutboxRelayResult result = outboxRelayService.relayPendingEvents(
                before + 1
        );

        assertThat(result.scanned()).isGreaterThanOrEqualTo(1);
        assertThat(result.published()).isGreaterThanOrEqualTo(1);
        assertThat(result.failed()).isZero();

        OutboxEvent published = outboxEventRepository.findAll()
                .stream()
                .filter(event -> event.id().equals(pending.id()))
                .findFirst()
                .orElseThrow();
        assertThat(published.status()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(published.publishedAt()).isNotNull();
        assertThat(published.lastError()).isNull();
    }

    @Test
    void shouldWriteBlockUpdatedEventAfterOptimisticUpdate()
            throws Exception {
        String token = registerAndGetAccessToken();
        String workspaceId = createWorkspace(token).get("id").asText();
        String documentId = createDocument(token, workspaceId, "Block outbox")
                .get("id")
                .asText();
        JsonNode block = createBlock(token, documentId, "Original");
        int before = outboxEventRepository.findAll().size();

        mockMvc.perform(patch(
                        "/api/v1/documents/{documentId}/blocks/{blockId}",
                        documentId,
                        block.get("id").asText()
                )
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBlockBody("Updated", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertThat(events).hasSize(before + 1);
        OutboxEvent event = events.get(events.size() - 1);
        assertThat(event.aggregateType()).isEqualTo("DOCUMENT_BLOCK");
        assertThat(event.aggregateId().toString())
                .isEqualTo(block.get("id").asText());
        assertThat(event.eventType()).isEqualTo("DOCUMENT_BLOCK_UPDATED");

        JsonNode payload = objectMapper.readTree(event.payload());
        assertThat(payload.get("documentId").asText()).isEqualTo(documentId);
        assertThat(payload.get("blockId").asText())
                .isEqualTo(block.get("id").asText());
        assertThat(payload.get("version").asLong()).isEqualTo(1);
    }

    @Test
    void shouldWriteDocumentAndBlockLifecycleEvents() throws Exception {
        String token = registerAndGetAccessToken();
        String workspaceId = createWorkspace(token).get("id").asText();
        String documentId = createDocument(token, workspaceId, "Lifecycle")
                .get("id")
                .asText();
        String parentDocumentId = createDocument(token, workspaceId, "Parent")
                .get("id")
                .asText();
        String firstBlockId = createBlock(token, documentId, "First")
                .get("id")
                .asText();
        String secondBlockId = createBlock(token, documentId, "Second")
                .get("id")
                .asText();

        int before = outboxEventRepository.findAll().size();

        mockMvc.perform(patch("/api/v1/documents/{id}", documentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DocumentTitleBody("Lifecycle updated")
                        )))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/documents/{id}/parent", documentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MoveDocumentBody(parentDocumentId)
                        )))
                .andExpect(status().isOk());

        mockMvc.perform(patch(
                        "/api/v1/documents/{documentId}/blocks/{blockId}/position",
                        documentId,
                        secondBlockId
                )
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MoveBlockBody(0)
                        )))
                .andExpect(status().isOk());

        mockMvc.perform(delete(
                        "/api/v1/documents/{documentId}/blocks/{blockId}",
                        documentId,
                        firstBlockId
                )
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/documents/{id}", documentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        List<String> newEventTypes = outboxEventRepository.findAll()
                .stream()
                .skip(before)
                .map(OutboxEvent::eventType)
                .toList();

        assertThat(newEventTypes).containsExactly(
                "DOCUMENT_UPDATED",
                "DOCUMENT_MOVED",
                "DOCUMENT_BLOCK_MOVED",
                "DOCUMENT_BLOCK_DELETED",
                "DOCUMENT_DELETED"
        );
    }

    @Test
    void shouldNotWriteOutboxEventWhenWorkspaceAccessDenied()
            throws Exception {
        String ownerToken = registerAndGetAccessToken();
        String outsiderToken = registerAndGetAccessToken();
        String workspaceId = createWorkspace(ownerToken).get("id").asText();

        int before = outboxEventRepository.findAll().size();
        mockMvc.perform(
                        post("/api/v1/workspaces/{id}/documents", workspaceId)
                                .header(HttpHeaders.AUTHORIZATION,
                                        bearer(outsiderToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(documentBody("Forbidden doc"))
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("WORKSPACE_ACCESS_DENIED"));

        assertThat(outboxEventRepository.findAll()).hasSize(before);
    }

    @Test
    void shouldNotWriteOutboxEventWhenBlockVersionConflicts()
            throws Exception {
        String token = registerAndGetAccessToken();
        String workspaceId = createWorkspace(token).get("id").asText();
        String documentId = createDocument(token, workspaceId, "Conflict")
                .get("id")
                .asText();
        String blockId = createBlock(token, documentId, "Original")
                .get("id")
                .asText();

        mockMvc.perform(patch(
                        "/api/v1/documents/{documentId}/blocks/{blockId}",
                        documentId,
                        blockId
                )
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBlockBody("First update", 0)))
                .andExpect(status().isOk());

        int before = outboxEventRepository.findAll().size();
        mockMvc.perform(patch(
                        "/api/v1/documents/{documentId}/blocks/{blockId}",
                        documentId,
                        blockId
                )
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBlockBody("Stale update", 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("DOCUMENT_BLOCK_VERSION_CONFLICT"));

        assertThat(outboxEventRepository.findAll()).hasSize(before);
    }

    private JsonNode createWorkspace(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/workspaces")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new WorkspaceBody("Outbox workspace")
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
                                .content(documentBody(title))
                )
                .andExpect(status().isCreated())
                .andReturn();
        return responseJson(result);
    }

    private JsonNode createBlock(
            String token,
            String documentId,
            String text
    ) throws Exception {
        MvcResult result = mockMvc.perform(
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
                .andExpect(status().isCreated())
                .andReturn();
        return responseJson(result);
    }

    private String registerAndGetAccessToken() throws Exception {
        String username = "outbox_" + UUID.randomUUID()
                .toString()
                .substring(0, 8);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterBody(
                                        username,
                                        "Outbox Tester",
                                        "password123"
                                )
                        )))
                .andExpect(status().isCreated())
                .andReturn();
        return responseJson(result).get("accessToken").asText();
    }

    private String documentBody(String title) throws Exception {
        return objectMapper.writeValueAsString(
                new DocumentBody(null, title)
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

    private record WorkspaceBody(String name) {
    }

    private record DocumentBody(
            String parentDocumentId,
            String title
    ) {
    }

    private record DocumentTitleBody(String title) {
    }

    private record MoveDocumentBody(String parentDocumentId) {
    }

    private record BlockBody(
            String type,
            BlockContentBody content
    ) {
    }

    private record BlockContentBody(String text) {
    }

    private record UpdateBlockBody(
            BlockContentBody content,
            long expectedVersion
    ) {
    }

    private record MoveBlockBody(int targetIndex) {
    }
}
