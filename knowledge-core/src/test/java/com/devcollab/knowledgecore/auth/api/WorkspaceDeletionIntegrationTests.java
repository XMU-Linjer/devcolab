package com.devcollab.knowledgecore.auth.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WorkspaceDeletionIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void adminDeletesWorkspaceAndAllCascadedData() throws Exception {
        AuthSession admin = register();
        AuthSession member = register();
        String workspaceId = createWorkspace(admin.token(), "待删除工作区");
        inviteMember(admin.token(), workspaceId, member.username());
        String documentId = createDocument(admin.token(), workspaceId);
        String repositoryId = registerRepository(admin.token(), workspaceId);

        mockMvc.perform(delete("/api/v1/workspaces/{id}", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/workspaces/{id}", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/workspaces")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        assertCount("workspaces", "id", workspaceId, 0);
        assertCount("workspace_members", "workspace_id", workspaceId, 0);
        assertCount("documents", "workspace_id", workspaceId, 0);
        assertCount("document_blocks", "document_id", documentId, 0);
        assertCount("git_repositories", "workspace_id", workspaceId, 0);
        assertCount("git_repository_files", "repository_id", repositoryId, 0);
        assertCount("git_changes", "repository_id", repositoryId, 0);
        assertCount("code_document_bindings", "workspace_id", workspaceId, 0);
        assertCount("notifications", "workspace_id", workspaceId, 0);

        Integer deleteEvents = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM outbox_events
                 WHERE aggregate_id = ?
                   AND event_type = 'GIT_REPOSITORY_DELETE_REQUESTED'
                """, Integer.class, UUID.fromString(repositoryId));
        assertThat(deleteEvents).isEqualTo(1);
    }

    @Test
    void memberCannotDeleteWorkspace() throws Exception {
        AuthSession admin = register();
        AuthSession member = register();
        String workspaceId = createWorkspace(admin.token(), "成员不可删除");
        inviteMember(admin.token(), workspaceId, member.username());

        mockMvc.perform(delete("/api/v1/workspaces/{id}", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(member.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORKSPACE_ACCESS_DENIED"));

        assertCount("workspaces", "id", workspaceId, 1);
    }

    @Test
    void hiddenOrMissingWorkspaceReturnsNotFound() throws Exception {
        AuthSession owner = register();
        AuthSession outsider = register();
        String workspaceId = createWorkspace(owner.token(), "其他用户工作区");

        mockMvc.perform(delete("/api/v1/workspaces/{id}", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider.token())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));

        mockMvc.perform(delete("/api/v1/workspaces/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));
    }

    private void assertCount(
            String table,
            String column,
            String id,
            int expected
    ) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Integer.class,
                UUID.fromString(id)
        );
        assertThat(count).isEqualTo(expected);
    }

    private String createWorkspace(
            String token,
            String name
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/workspaces")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return responseJson(result).get("id").asText();
    }

    private String createDocument(
            String token,
            String workspaceId
    ) throws Exception {
        MvcResult result = mockMvc.perform(post(
                        "/api/v1/workspaces/{id}/documents",
                        workspaceId
                )
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"删除级联文档","documentType":"REQUIREMENT"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return responseJson(result).get("id").asText();
    }

    private String registerRepository(
            String token,
            String workspaceId
    ) throws Exception {
        MvcResult result = mockMvc.perform(post(
                        "/api/v1/workspaces/{id}/git/repositories",
                        workspaceId
                )
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Delete Fixture",
                                  "provider":"GENERIC",
                                  "remoteUrl":"https://example.com/delete-fixture.git",
                                  "defaultBranch":"main"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return responseJson(result).get("id").asText();
    }

    private void inviteMember(
            String token,
            String workspaceId,
            String username
    ) throws Exception {
        mockMvc.perform(post(
                        "/api/v1/workspaces/{id}/members/invitations",
                        workspaceId
                )
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","role":"MEMBER"}
                                """.formatted(username)))
                .andExpect(status().isCreated());
    }

    private AuthSession register() throws Exception {
        String username = "delete_" + UUID.randomUUID()
                .toString().substring(0, 8);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"%s",
                                  "displayName":"删除测试用户",
                                  "password":"password123"
                                }
                                """.formatted(username)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = responseJson(result);
        return new AuthSession(
                response.get("username").asText(),
                response.get("accessToken").asText()
        );
    }

    private JsonNode responseJson(MvcResult result) throws Exception {
        return objectMapper.readTree(
                result.getResponse().getContentAsString()
        );
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record AuthSession(String username, String token) {
    }
}
