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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WorkspaceMemberIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldInviteListAndUpdateWorkspaceMember() throws Exception {
        AuthSession admin = register();
        AuthSession member = register();
        String workspaceId = createWorkspace(admin.token()).get("id").asText();

        mockMvc.perform(post(
                        "/api/v1/workspaces/{id}/members/invitations",
                        workspaceId
                )
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inviteBody(member.username(), "MEMBER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(member.userId()))
                .andExpect(jsonPath("$.username").value(member.username()))
                .andExpect(jsonPath("$.role").value("MEMBER"));

        mockMvc.perform(get("/api/v1/workspaces/{id}/members", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(member.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("ADMIN"))
                .andExpect(jsonPath("$[1].role").value("MEMBER"));

        mockMvc.perform(patch(
                        "/api/v1/workspaces/{workspaceId}/members/{userId}/role",
                        workspaceId,
                        member.userId()
                )
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void shouldRejectMemberManagementFromNormalMember() throws Exception {
        AuthSession admin = register();
        AuthSession member = register();
        AuthSession target = register();
        String workspaceId = createWorkspace(admin.token()).get("id").asText();
        inviteMember(admin.token(), workspaceId, member.username(), "MEMBER");

        mockMvc.perform(post(
                        "/api/v1/workspaces/{id}/members/invitations",
                        workspaceId
                )
                        .header(HttpHeaders.AUTHORIZATION, bearer(member.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inviteBody(target.username(), "MEMBER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("WORKSPACE_ACCESS_DENIED"));
    }

    @Test
    void shouldRejectRemovingLastAdmin() throws Exception {
        AuthSession admin = register();
        String workspaceId = createWorkspace(admin.token()).get("id").asText();

        mockMvc.perform(delete(
                        "/api/v1/workspaces/{workspaceId}/members/{userId}",
                        workspaceId,
                        admin.userId()
                )
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORKSPACE_LAST_ADMIN"));

        mockMvc.perform(patch(
                        "/api/v1/workspaces/{workspaceId}/members/{userId}/role",
                        workspaceId,
                        admin.userId()
                )
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORKSPACE_LAST_ADMIN"));
    }

    @Test
    void shouldRejectDuplicatedMemberInvite() throws Exception {
        AuthSession admin = register();
        AuthSession member = register();
        String workspaceId = createWorkspace(admin.token()).get("id").asText();
        inviteMember(admin.token(), workspaceId, member.username(), "MEMBER");

        mockMvc.perform(post(
                        "/api/v1/workspaces/{id}/members/invitations",
                        workspaceId
                )
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inviteBody(member.username(), "MEMBER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORKSPACE_MEMBER_EXISTS"));
    }

    private JsonNode createWorkspace(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/workspaces")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"成员权限空间\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentUserRole").value("ADMIN"))
                .andReturn();
        return responseJson(result);
    }

    private void inviteMember(
            String token,
            String workspaceId,
            String username,
            String role
    ) throws Exception {
        mockMvc.perform(post(
                        "/api/v1/workspaces/{id}/members/invitations",
                        workspaceId
                )
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inviteBody(username, role)))
                .andExpect(status().isCreated());
    }

    private String inviteBody(String username, String role) {
        return """
                {"username": "%s", "role": "%s"}
                """.formatted(username, role);
    }

    private AuthSession register() throws Exception {
        String username = "user_" + UUID.randomUUID()
                .toString().substring(0, 8);
        String requestBody = """
                {
                  "username": "%s",
                  "displayName": "测试用户",
                  "password": "password123"
                }
                """.formatted(username);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = responseJson(result);
        return new AuthSession(
                response.get("userId").asText(),
                response.get("username").asText(),
                response.get("accessToken").asText()
        );
    }

    private JsonNode responseJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record AuthSession(
            String userId,
            String username,
            String token
    ) {
    }
}
