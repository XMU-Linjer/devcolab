package com.devcollab.mcp;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpServerProtocolTests {

    private static final String SECRET = "devcollab-local-development-secret-change-me";

    @LocalServerPort
    private int port;

    @Test
    void officialSdkCanInitializeAndListZeroTools() {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder("http://localhost:" + port)
                .endpoint("/mcp")
                .requestBuilder(HttpRequest.newBuilder()
                        .header("Authorization", "Bearer " + issueAccessToken()))
                .build();

        try (McpSyncClient client = McpClient.sync(transport)
                .initializationTimeout(Duration.ofSeconds(5))
                .requestTimeout(Duration.ofSeconds(5))
                .build()) {
            assertThat(client.initialize().serverInfo().name()).isEqualTo("devcollab-context-server");
            assertThat(client.listTools().tools()).isEmpty();
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
}
