package com.devcollab.mcp.application;

import com.devcollab.mcp.client.KnowledgeCoreGateway;
import com.devcollab.mcp.config.McpProperties;
import com.devcollab.mcp.error.McpToolErrorCode;
import com.devcollab.mcp.error.McpToolException;
import com.devcollab.mcp.governance.ContextBudgetPolicy;
import com.devcollab.mcp.security.McpUserIdentity;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CodeReadApplicationServiceTests {

    private final KnowledgeCoreGateway gateway = mock(KnowledgeCoreGateway.class);
    private final UUID workspaceId = UUID.randomUUID();
    private final UUID repositoryId = UUID.randomUUID();
    private final McpUserIdentity identity =
            new McpUserIdentity(UUID.randomUUID(), UUID.randomUUID(), "member", "token");

    @Test
    void rejectsBinaryAndReportsUnavailableBindingsHonestly() {
        when(gateway.readRepositorySource(eq(workspaceId), eq(repositoryId), anyString(), any()))
                .thenReturn(new KnowledgeCoreGateway.RepositorySource(
                        repositoryId, "abc", "image.png", 100, "PNG", false, null
                ));
        CodeReadApplicationService service = service(400, 40_000);

        assertThatThrownBy(() -> service.read(
                workspaceId, repositoryId, "image.png", null, null, false, identity
        )).isInstanceOfSatisfying(McpToolException.class,
                error -> assertThat(error.code()).isEqualTo(McpToolErrorCode.UNSUPPORTED_FILE_TYPE));

        when(gateway.readRepositorySource(eq(workspaceId), eq(repositoryId), eq("App.java"), any()))
                .thenReturn(new KnowledgeCoreGateway.RepositorySource(
                        repositoryId, "abc", "App.java", 20, "Java", true, "one\ntwo"
                ));
        Map<String, Object> result = service.read(
                workspaceId, repositoryId, "App.java", null, null, true, identity
        );
        assertThat(result.get("existingBindings")).isEqualTo(List.of());
        assertThat(result.get("existingBindingsAvailable")).isEqualTo(false);
        assertThat(result.get("existingBindingsRequested")).isEqualTo(true);
    }

    @Test
    void appliesCentralLineAndCharacterBudgets() {
        String content = java.util.stream.IntStream.rangeClosed(1, 500)
                .mapToObj(line -> "line-" + line + "-" + "x".repeat(120))
                .collect(java.util.stream.Collectors.joining("\n"));
        when(gateway.readRepositorySource(eq(workspaceId), eq(repositoryId), anyString(), any()))
                .thenReturn(new KnowledgeCoreGateway.RepositorySource(
                        repositoryId, "abc", "App.java", content.length(), "Java", true, content
                ));
        CodeReadApplicationService service = service(400, 40_000);

        Map<String, Object> result = service.read(
                workspaceId, repositoryId, "App.java", null, null, false, identity
        );

        assertThat(result.get("truncated")).isEqualTo(true);
        assertThat((int) result.get("endLine")).isLessThanOrEqualTo(400);
        assertThat(((String) result.get("content")).length()).isLessThanOrEqualTo(40_000);
        assertThat((int) result.get("omittedLineCount")).isGreaterThan(0);
        assertThat((int) result.get("omittedCharacterCount")).isGreaterThan(0);
    }

    private CodeReadApplicationService service(int lines, int characters) {
        McpProperties properties = new McpProperties(
                "/mcp", "test", "1", lines, characters, 2_048,
                List.of("http://localhost:*"), List.of("localhost:*"),
                URI.create("http://localhost:8080"), Duration.ofSeconds(1)
        );
        return new CodeReadApplicationService(gateway, new ContextBudgetPolicy(properties));
    }
}
