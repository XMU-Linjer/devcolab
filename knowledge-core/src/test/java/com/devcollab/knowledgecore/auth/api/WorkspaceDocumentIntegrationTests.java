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
class WorkspaceDocumentIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCompleteWorkspaceAndDocumentTreeChain() throws Exception {
        String token = registerAndGetAccessToken();
        JsonNode workspace = createWorkspace(token, "实验室项目");
        String workspaceId = workspace.get("id").asText();

        mockMvc.perform(get("/api/v1/workspaces")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        mockMvc.perform(get("/api/v1/workspaces/{id}", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentUserRole").value("OWNER"));

        JsonNode root = createDocument(
                token, workspaceId, null, "总体架构"
        );
        JsonNode child = createDocument(
                token, workspaceId, root.get("id").asText(), "登录设计"
        );

        mockMvc.perform(
                        get("/api/v1/workspaces/{id}/documents/tree", workspaceId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(root.get("id").asText()))
                .andExpect(jsonPath("$[0].title").value("总体架构"))
                .andExpect(jsonPath("$[0].children[0].id")
                        .value(child.get("id").asText()))
                .andExpect(jsonPath("$[0].children[0].title")
                        .value("登录设计"));

        mockMvc.perform(get(
                        "/api/v1/documents/{id}", child.get("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceId").value(workspaceId))
                .andExpect(jsonPath("$.parentDocumentId")
                        .value(root.get("id").asText()))
                .andExpect(jsonPath("$.title").value("登录设计"));
    }

    @Test
    void shouldRejectCrossWorkspaceAccess() throws Exception {
        String ownerToken = registerAndGetAccessToken();
        String outsiderToken = registerAndGetAccessToken();
        String workspaceId = createWorkspace(
                ownerToken, "私有空间"
        ).get("id").asText();
        JsonNode document = createDocument(
                ownerToken, workspaceId, null, "内部设计"
        );

        mockMvc.perform(get("/api/v1/workspaces/{id}", workspaceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsiderToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("WORKSPACE_ACCESS_DENIED"));

        mockMvc.perform(get(
                        "/api/v1/documents/{id}", document.get("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsiderToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("WORKSPACE_ACCESS_DENIED"));
    }

    @Test
    void shouldRejectParentFromAnotherWorkspace() throws Exception {
        String token = registerAndGetAccessToken();
        String firstWorkspaceId = createWorkspace(
                token, "空间一"
        ).get("id").asText();
        String secondWorkspaceId = createWorkspace(
                token, "空间二"
        ).get("id").asText();
        String foreignParentId = createDocument(
                token, firstWorkspaceId, null, "空间一文档"
        ).get("id").asText();

        mockMvc.perform(
                        post("/api/v1/workspaces/{id}/documents", secondWorkspaceId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(documentBody(foreignParentId, "错误子文档"))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("DOCUMENT_PARENT_INVALID"));
    }

    @Test
    void shouldRequireAuthenticationForWorkspaceCreation() throws Exception {
        mockMvc.perform(post("/api/v1/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"未登录空间\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_INVALID"));
    }

    private JsonNode createWorkspace(String token, String name)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/workspaces")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentUserRole").value("OWNER"))
                .andReturn();
        return responseJson(result);
    }

    private JsonNode createDocument(
            String token,
            String workspaceId,
            String parentDocumentId,
            String title
    ) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/v1/workspaces/{id}/documents", workspaceId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(documentBody(parentDocumentId, title))
                )
                .andExpect(status().isCreated())
                .andReturn();
        return responseJson(result);
    }

    private String documentBody(String parentDocumentId, String title) {
        String parent = parentDocumentId == null
                ? "null"
                : "\"" + parentDocumentId + "\"";
        return """
                {"parentDocumentId": %s, "title": "%s"}
                """.formatted(parent, title);
    }

    private String registerAndGetAccessToken() throws Exception {
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
        return responseJson(result).get("accessToken").asText();
    }

    private JsonNode responseJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
