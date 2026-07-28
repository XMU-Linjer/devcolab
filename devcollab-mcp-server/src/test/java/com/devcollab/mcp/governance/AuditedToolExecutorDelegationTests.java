package com.devcollab.mcp.governance;

import com.devcollab.mcp.error.McpToolErrorCode;
import com.devcollab.mcp.error.McpToolException;
import com.devcollab.mcp.security.McpUserIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditedToolExecutorDelegationTests {

    private static final String CODE_READ = "devcollab.code.read";

    private final AuditedToolExecutor executor =
            new AuditedToolExecutor(event -> { }, new ObjectMapper());

    @Test
    void allowsToolWithinDelegatedWorkspaceAndRepository() {
        UUID workspaceId = UUID.randomUUID();
        UUID repositoryId = UUID.randomUUID();
        McpUserIdentity identity = delegated(workspaceId, repositoryId, Set.of(CODE_READ));

        Map<String, Object> result = executor.execute(
                CODE_READ,
                identity,
                workspaceId,
                repositoryId,
                Map.of("repositoryId", repositoryId.toString()),
                () -> Map.of("content", "ok")
        );

        assertThat(result).containsEntry("content", "ok");
    }

    @Test
    void rejectsDifferentWorkspaceRepositoryOrTool() {
        UUID workspaceId = UUID.randomUUID();
        UUID repositoryId = UUID.randomUUID();
        McpUserIdentity identity = delegated(workspaceId, repositoryId, Set.of(CODE_READ));

        assertDenied(() -> executor.execute(
                CODE_READ, identity, UUID.randomUUID(), repositoryId,
                Map.of(), Map::of
        ));
        assertDenied(() -> executor.execute(
                CODE_READ, identity, workspaceId, UUID.randomUUID(),
                Map.of(), Map::of
        ));
        assertDenied(() -> executor.execute(
                "devcollab.document.get_structure", identity, workspaceId, repositoryId,
                Map.of(), Map::of
        ));
    }

    @Test
    void rejectsDifferentRepositoryHiddenInNestedArguments() {
        UUID workspaceId = UUID.randomUUID();
        UUID repositoryId = UUID.randomUUID();
        McpUserIdentity identity = delegated(workspaceId, repositoryId, Set.of(CODE_READ));
        Map<String, Object> arguments = Map.of(
                "proposals",
                List.of(Map.of("repositoryId", UUID.randomUUID().toString()))
        );

        assertDenied(() -> executor.execute(
                CODE_READ, identity, workspaceId, repositoryId, arguments, Map::of
        ));
    }

    private McpUserIdentity delegated(
            UUID workspaceId,
            UUID repositoryId,
            Set<String> allowedTools
    ) {
        return new McpUserIdentity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "agent",
                "not-persisted",
                "agent_delegation",
                workspaceId,
                repositoryId,
                UUID.randomUUID(),
                "abc123",
                allowedTools
        );
    }

    private void assertDenied(Runnable invocation) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(McpToolException.class, exception ->
                        assertThat(exception.code())
                                .isEqualTo(McpToolErrorCode.PERMISSION_DENIED));
    }
}
