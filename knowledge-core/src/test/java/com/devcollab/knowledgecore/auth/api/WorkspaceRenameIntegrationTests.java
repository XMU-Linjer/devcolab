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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WorkspaceRenameIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminRenamesWorkspaceAndReadsPersistedName() throws Exception {
        AuthSession admin = register();
        String workspaceId = createWorkspace(admin.token(), "原工作区名称");

        mockMvc.perform(patch("/api/v1/workspaces/{id}", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  新工作区名称  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(workspaceId))
                .andExpect(jsonPath("$.name").value("新工作区名称"))
                .andExpect(jsonPath("$.currentUserRole").value("ADMIN"));

        mockMvc.perform(get("/api/v1/workspaces/{id}", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("新工作区名称"));

        mockMvc.perform(get("/api/v1/workspaces")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("新工作区名称"));
    }

    @Test
    void memberCannotRenameWorkspace() throws Exception {
        AuthSession admin = register();
        AuthSession member = register();
        String workspaceId = createWorkspace(admin.token(), "管理员工作区");
        inviteMember(admin.token(), workspaceId, member.username());

        mockMvc.perform(patch("/api/v1/workspaces/{id}", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(member.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"成员越权修改\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORKSPACE_ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/workspaces/{id}", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("管理员工作区"));
    }

    @Test
    void hiddenOrMissingWorkspaceReturnsNotFound() throws Exception {
        AuthSession owner = register();
        AuthSession outsider = register();
        String workspaceId = createWorkspace(owner.token(), "私有工作区");

        mockMvc.perform(patch("/api/v1/workspaces/{id}", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"不可见修改\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));

        mockMvc.perform(patch(
                                "/api/v1/workspaces/{id}",
                                UUID.randomUUID()
                        )
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"不存在\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));
    }

    @Test
    void rejectsBlankOrOversizedNames() throws Exception {
        AuthSession admin = register();
        String workspaceId = createWorkspace(admin.token(), "校验工作区");

        mockMvc.perform(patch("/api/v1/workspaces/{id}", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(patch("/api/v1/workspaces/{id}", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RenameBody("a".repeat(101))
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    private String createWorkspace(
            String token,
            String name
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/workspaces")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RenameBody(name)
                        )))
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
        String username = "rename_" + UUID.randomUUID()
                .toString().substring(0, 8);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"%s",
                                  "displayName":"重命名测试用户",
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

    private record RenameBody(String name) {
    }
}
