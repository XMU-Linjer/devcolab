package com.devcollab.knowledgecore.auth.api;

import com.devcollab.knowledgecore.common.outbox.application.OutboxEventPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(WorkspaceDeletionRollbackIntegrationTests.FailingOutboxConfiguration.class)
class WorkspaceDeletionRollbackIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void rollsBackDatabaseDeletionWhenCleanupEventCannotBeStored()
            throws Exception {
        AuthSession admin = register();
        UUID workspaceId = UUID.fromString(
                createWorkspace(admin.token()).get("id").asText()
        );
        UUID repositoryId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO git_repositories
                    (id, workspace_id, name, provider, remote_url,
                     default_branch, created_by, created_at, updated_at,
                     sync_status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                repositoryId,
                workspaceId,
                "Rollback Fixture",
                "GENERIC",
                "https://example.com/rollback.git",
                "main",
                admin.userId(),
                Timestamp.from(now),
                Timestamp.from(now),
                "READY"
        );

        mockMvc.perform(delete("/api/v1/workspaces/{id}", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isInternalServerError());

        Integer workspaceCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workspaces WHERE id = ?",
                Integer.class,
                workspaceId
        );
        Integer repositoryCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM git_repositories WHERE id = ?",
                Integer.class,
                repositoryId
        );
        assertThat(workspaceCount).isEqualTo(1);
        assertThat(repositoryCount).isEqualTo(1);
    }

    private JsonNode createWorkspace(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/workspaces")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"事务回滚空间\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(
                result.getResponse().getContentAsString()
        );
    }

    private AuthSession register() throws Exception {
        String username = "rollback_" + UUID.randomUUID()
                .toString().substring(0, 8);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"%s",
                                  "displayName":"回滚测试用户",
                                  "password":"password123"
                                }
                                """.formatted(username)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = objectMapper.readTree(
                result.getResponse().getContentAsString()
        );
        return new AuthSession(
                UUID.fromString(response.get("userId").asText()),
                response.get("accessToken").asText()
        );
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record AuthSession(UUID userId, String token) {
    }

    @TestConfiguration
    static class FailingOutboxConfiguration {

        @Bean
        @Primary
        OutboxEventPublisher failingOutboxEventPublisher() {
            OutboxEventPublisher publisher = mock(OutboxEventPublisher.class);
            when(publisher.publish(
                    anyString(),
                    any(UUID.class),
                    anyString(),
                    anyMap()
            )).thenThrow(new IllegalStateException("outbox unavailable"));
            return publisher;
        }
    }
}
