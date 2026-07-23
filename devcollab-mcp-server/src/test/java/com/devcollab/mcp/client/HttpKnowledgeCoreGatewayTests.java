package com.devcollab.mcp.client;

import com.devcollab.mcp.error.McpToolErrorCode;
import com.devcollab.mcp.error.McpToolException;
import com.devcollab.mcp.security.McpUserIdentity;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpKnowledgeCoreGatewayTests {

    private HttpServer server;
    private HttpKnowledgeCoreGateway gateway;
    private final UUID workspaceId = UUID.randomUUID();
    private final UUID repositoryId = UUID.randomUUID();
    private final AtomicReference<String> authorization = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", this::respond);
        server.start();
        gateway = new HttpKnowledgeCoreGateway(RestClient.builder()
                .baseUrl("http://localhost:" + server.getAddress().getPort())
                .build());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void forwardsBearerAndMapsRealWorkspaceRepositoryAndSourceFields() {
        KnowledgeCoreGateway.WorkspaceContext workspace =
                gateway.getWorkspaceContext(workspaceId, identity());
        KnowledgeCoreGateway.RepositorySource source =
                gateway.readRepositorySource(workspaceId, repositoryId, "src/App.java", identity());

        assertThat(authorization.get()).isEqualTo("Bearer test-access-token");
        assertThat(workspace.name()).isEqualTo("Core Workspace");
        assertThat(workspace.repositories()).singleElement()
                .satisfies(repository -> {
                    assertThat(repository.repositoryId()).isEqualTo(repositoryId);
                    assertThat(repository.syncStatus()).isEqualTo("SYNCED");
                });
        assertThat(source.content()).isEqualTo("class App {}");
        assertThat(source.commitSha()).isEqualTo("abc123");
    }

    @Test
    void mapsPermissionRepositoryFileAndAvailabilityFailures() {
        assertThatThrownBy(() -> gateway.getWorkspaceContext(UUID.fromString(
                        "00000000-0000-0000-0000-000000000403"), identity()))
                .isInstanceOfSatisfying(McpToolException.class,
                        error -> assertThat(error.code()).isEqualTo(McpToolErrorCode.PERMISSION_DENIED));

        assertThatThrownBy(() -> gateway.readRepositorySource(
                        workspaceId,
                        UUID.fromString("00000000-0000-0000-0000-000000000404"),
                        "src/App.java",
                        identity()))
                .isInstanceOfSatisfying(McpToolException.class,
                        error -> assertThat(error.code()).isEqualTo(McpToolErrorCode.REPOSITORY_NOT_FOUND));

        server.stop(0);
        assertThatThrownBy(() -> gateway.getWorkspaceContext(workspaceId, identity()))
                .isInstanceOfSatisfying(McpToolException.class,
                        error -> assertThat(error.code()).isEqualTo(McpToolErrorCode.CORE_UNAVAILABLE));
    }

    private void respond(HttpExchange exchange) throws IOException {
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        String path = exchange.getRequestURI().getPath();
        if (path.contains("00000000-0000-0000-0000-000000000403")) {
            json(exchange, 403, "{\"code\":\"WORKSPACE_ACCESS_DENIED\"}");
            return;
        }
        if (path.contains("00000000-0000-0000-0000-000000000404")) {
            json(exchange, 404, "{\"code\":\"GIT_REPOSITORY_NOT_FOUND\"}");
            return;
        }
        if (path.endsWith("/git/repositories")) {
            json(exchange, 200, """
                    [{"id":"%s","name":"devcollab","provider":"GITHUB",
                    "remoteUrl":"https://github.com/example/devcollab","defaultBranch":"main",
                    "syncStatus":"SYNCED","lastSyncedCommit":"abc123"}]
                    """.formatted(repositoryId));
            return;
        }
        if (path.endsWith("/source")) {
            json(exchange, 200, """
                    {"repositoryId":"%s","commitSha":"abc123","path":"src/App.java",
                    "sizeBytes":12,"language":"Java","readable":true,"content":"class App {}"}
                    """.formatted(repositoryId));
            return;
        }
        json(exchange, 200, """
                {"id":"%s","name":"Core Workspace","currentUserRole":"MEMBER"}
                """.formatted(workspaceId));
    }

    private void json(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private McpUserIdentity identity() {
        return new McpUserIdentity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "member",
                "test-access-token"
        );
    }
}
