package com.devcollab.mcp;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.devcollab.mcp.client.KnowledgeCoreGateway;
import com.devcollab.mcp.error.McpToolErrorCode;
import com.devcollab.mcp.error.McpToolException;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpServerProtocolTests {

    private static final String SECRET = "devcollab-local-development-secret-change-me";

    @LocalServerPort
    private int port;

    @MockitoBean
    private KnowledgeCoreGateway coreGateway;

    @Test
    void officialSdkCanInitializeAndListTwoTools() {
        try (McpSyncClient client = client()) {
            assertThat(client.initialize().serverInfo().name()).isEqualTo("devcollab-context-server");
            List<McpSchema.Tool> tools = client.listTools().tools();
            assertThat(tools)
                    .extracting(McpSchema.Tool::name)
                    .containsExactlyInAnyOrder(
                            "devcollab.workspace.get_context",
                            "devcollab.code.read",
                            "devcollab.document.get_structure",
                            "devcollab.binding.list"
                    );
            for (McpSchema.Tool tool : tools) {
                assertThat(tool.inputSchema())
                        .containsEntry("type", "object")
                        .containsEntry("additionalProperties", false);
                assertThat(tool.annotations().readOnlyHint()).isTrue();
                assertThat(tool.annotations().destructiveHint()).isFalse();
                assertThat(tool.annotations().idempotentHint()).isTrue();
                assertThat(tool.annotations().openWorldHint()).isFalse();
            }
            McpSchema.Tool workspaceTool = tools.stream()
                    .filter(tool -> tool.name().equals("devcollab.workspace.get_context"))
                    .findFirst()
                    .orElseThrow();
            assertThat(workspaceTool.inputSchema().get("required"))
                    .isEqualTo(List.of("workspaceId"));
            McpSchema.Tool codeTool = tools.stream()
                    .filter(tool -> tool.name().equals("devcollab.code.read"))
                    .findFirst()
                    .orElseThrow();
            assertThat(codeTool.inputSchema().get("required"))
                    .isEqualTo(List.of("workspaceId", "repositoryId", "path"));
            assertThat(workspaceTool.outputSchema())
                    .containsKeys("type", "properties", "oneOf", "additionalProperties");
            assertThat(codeTool.outputSchema())
                    .containsKeys("type", "properties", "oneOf", "additionalProperties");
            assertThat(((Map<?, ?>) workspaceTool.outputSchema().get("properties")).containsKey("error"))
                    .isTrue();
            assertThat(((Map<?, ?>) codeTool.outputSchema().get("properties")).containsKey("error"))
                    .isTrue();
        }
    }

    @Test
    void authenticatedMemberCanReadWorkspaceAndCodeThroughOfficialClient() {
        UUID workspaceId = UUID.randomUUID();
        UUID repositoryId = UUID.randomUUID();
        when(coreGateway.getWorkspaceContext(eq(workspaceId), any())).thenReturn(
                new KnowledgeCoreGateway.WorkspaceContext(
                        workspaceId,
                        "MCP Workspace",
                        "MEMBER",
                        List.of(new KnowledgeCoreGateway.RepositoryContext(
                                repositoryId,
                                "devcollab",
                                "GITHUB",
                                "https://github.com/example/devcollab",
                                "main",
                                "SYNCED",
                                "abc123"
                        ))
                )
        );
        when(coreGateway.readRepositorySource(eq(workspaceId), eq(repositoryId), eq("src/App.java"), any()))
                .thenReturn(new KnowledgeCoreGateway.RepositorySource(
                        repositoryId,
                        "abc123",
                        "src/App.java",
                        30,
                        "Java",
                        true,
                        "line one\nline two\nline three"
                ));

        try (McpSyncClient client = client()) {
            client.initialize();
            McpSchema.CallToolResult workspace = client.callTool(new McpSchema.CallToolRequest(
                    "devcollab.workspace.get_context",
                    Map.of("workspaceId", workspaceId.toString())
            ));
            McpSchema.CallToolResult code = client.callTool(new McpSchema.CallToolRequest(
                    "devcollab.code.read",
                    Map.of(
                            "workspaceId", workspaceId.toString(),
                            "repositoryId", repositoryId.toString(),
                            "path", "src/App.java",
                            "startLine", 2,
                            "endLine", 3
                    )
            ));

            assertThat(workspace.isError()).isFalse();
            assertThat(((Map<?, ?>) workspace.structuredContent()).get("currentUserRole"))
                    .isEqualTo("MEMBER");
            assertThat(code.isError()).isFalse();
            assertThat(((Map<?, ?>) code.structuredContent()).get("content"))
                    .isEqualTo("line two\nline three");
            assertThat(((Map<?, ?>) code.structuredContent()).get("truncated"))
                    .isEqualTo(false);
        }
    }

    @Test
    void corePermissionFailureIsStructured() {
        UUID workspaceId = UUID.randomUUID();
        when(coreGateway.getWorkspaceContext(eq(workspaceId), any())).thenThrow(
                new McpToolException(McpToolErrorCode.PERMISSION_DENIED, "Workspace access was denied")
        );

        try (McpSyncClient client = client()) {
            client.initialize();
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                    "devcollab.workspace.get_context",
                    Map.of("workspaceId", workspaceId.toString())
            ));

            assertThat(result.isError()).isTrue();
            Map<?, ?> error = (Map<?, ?>) ((Map<?, ?>) result.structuredContent()).get("error");
            assertThat(error.get("code")).isEqualTo("PERMISSION_DENIED");
            assertThat(error.get("retryable")).isEqualTo(false);
        }
    }

    @Test
    void anonymousMcpRequestIsRejected() throws Exception {
        String initializeRequest = """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                  "protocolVersion":"2025-06-18",
                  "capabilities":{},
                  "clientInfo":{"name":"security-test","version":"1.0.0"}
                }}
                """;
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json, text/event-stream")
                        .POST(HttpRequest.BodyPublishers.ofString(initializeRequest))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(401);
    }

    private String issueAccessToken() {
        Instant now = Instant.now();
        return JWT.create()
                .withIssuer("devcollab-knowledge-core")
                .withAudience("devcollab-web")
                .withSubject(UUID.randomUUID().toString())
                .withJWTId(UUID.randomUUID().toString())
                .withClaim("sid", UUID.randomUUID().toString())
                .withClaim("username", "mcp-protocol-test")
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plusSeconds(300)))
                .sign(Algorithm.HMAC256(SECRET));
    }

    private McpSyncClient client() {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder("http://localhost:" + port)
                .endpoint("/mcp")
                .requestBuilder(HttpRequest.newBuilder()
                        .header("Authorization", "Bearer " + issueAccessToken()))
                .build();
        return McpClient.sync(transport)
                .initializationTimeout(Duration.ofSeconds(5))
                .requestTimeout(Duration.ofSeconds(5))
                .build();
    }
}
