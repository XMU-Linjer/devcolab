package com.devcollab.mcp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.devcollab.mcp.client.KnowledgeCoreGateway;
import com.devcollab.mcp.config.McpProperties;
import com.devcollab.mcp.error.McpToolErrorCode;
import com.devcollab.mcp.error.McpToolException;
import com.devcollab.mcp.security.McpUserIdentity;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class FindCandidatesApplicationServiceTests {

    private KnowledgeCoreGateway gateway;
    private FindCandidatesApplicationService service;
    private McpUserIdentity identity;
    private UUID workspaceId;

    @BeforeEach
    void setUp() {
        gateway = mock(KnowledgeCoreGateway.class);
        McpProperties properties = new McpProperties(
                "/mcp", "test", "1", 400, 40000, 2048,
                100, 30000, 50, 20, 500,
                List.of("http://localhost:*"), List.of("localhost:*"),
                URI.create("http://localhost:8080"), Duration.ofSeconds(1)
        );
        service = new FindCandidatesApplicationService(gateway, properties);
        identity = new McpUserIdentity(UUID.randomUUID(), UUID.randomUUID(), "member", "token");
        workspaceId = UUID.randomUUID();
    }

    @Test
    void emptyCandidatesReturnEmptyList() {
        when(gateway.searchDocuments(eq(workspaceId), eq("test"), eq("ALL"), any(Integer.class), any()))
                .thenReturn(List.of());

        Map<String, Object> result = service.findCandidates(workspaceId, "test", null, null, identity);

        assertThat((List<?>) result.get("candidates")).isEmpty();
        assertThat(result.get("totalResults")).isEqualTo(0);
        assertThat(result.get("truncated")).isEqualTo(false);
        assertThat(result.get("omittedCount")).isEqualTo(0);
        assertThat(result.get("query")).isEqualTo("test");
        assertThat(result.get("scope")).isEqualTo("ALL");
        assertThat(result.get("workspaceId")).isEqualTo(workspaceId);
    }

    @Test
    void singleCandidateReturnsCorrectly() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        Instant now = Instant.now();
        when(gateway.searchDocuments(eq(workspaceId), eq("design"), eq("ALL"), any(Integer.class), any()))
                .thenReturn(List.of(
                        new KnowledgeCoreGateway.SearchCandidate(
                                "DOCUMENT_TITLE", documentId, "My Design Doc", null, null, now
                        )
                ));

        Map<String, Object> result = service.findCandidates(workspaceId, "design", null, null, identity);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) result.get("candidates");
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).get("type")).isEqualTo("DOCUMENT_TITLE");
        assertThat(candidates.get(0).get("documentId")).isEqualTo(documentId);
        assertThat(candidates.get(0).get("documentTitle")).isEqualTo("My Design Doc");
        assertThat(candidates.get(0).get("blockId")).isNull();
        assertThat(candidates.get(0).get("snippet")).isNull();
        assertThat(candidates.get(0).get("updatedAt")).isEqualTo(now.toString());
        assertThat(result.get("totalResults")).isEqualTo(1);
    }

    @Test
    void blockContentHitIncludesBlockIdAndSnippet() {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        when(gateway.searchDocuments(eq(workspaceId), eq("pattern"), eq("CONTENT"), any(Integer.class), any()))
                .thenReturn(List.of(
                        new KnowledgeCoreGateway.SearchCandidate(
                                "BLOCK_CONTENT", documentId, "API Doc", blockId,
                                "Observer pattern implementation", Instant.now()
                        )
                ));

        Map<String, Object> result = service.findCandidates(workspaceId, "pattern", "CONTENT", null, identity);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) result.get("candidates");
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).get("blockId")).isEqualTo(blockId);
        assertThat(candidates.get(0).get("snippet")).isEqualTo("Observer pattern implementation");
        assertThat(result.get("scope")).isEqualTo("CONTENT");
    }

    @Test
    void multipleCandidatesPreserveOrder() {
        UUID doc1 = UUID.randomUUID();
        UUID doc2 = UUID.randomUUID();
        UUID doc3 = UUID.randomUUID();
        when(gateway.searchDocuments(eq(workspaceId), eq("api"), eq("ALL"), any(Integer.class), any()))
                .thenReturn(List.of(
                        candidate("DOCUMENT_TITLE", doc1, "First", null, null),
                        candidate("BLOCK_CONTENT", doc2, "Second", UUID.randomUUID(), "snippet"),
                        candidate("DOCUMENT_TITLE", doc3, "Third", null, null)
                ));

        Map<String, Object> result = service.findCandidates(workspaceId, "api", null, null, identity);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) result.get("candidates");
        assertThat(candidates).hasSize(3);
        assertThat(candidates.get(0).get("documentTitle")).isEqualTo("First");
        assertThat(candidates.get(1).get("documentTitle")).isEqualTo("Second");
        assertThat(candidates.get(2).get("documentTitle")).isEqualTo("Third");
    }

    @Test
    void scopePassedThroughCorrectly() {
        when(gateway.searchDocuments(eq(workspaceId), eq("title"), eq("TITLE"), any(Integer.class), any()))
                .thenReturn(List.of());

        Map<String, Object> result = service.findCandidates(workspaceId, "title", "TITLE", null, identity);

        assertThat(result.get("scope")).isEqualTo("TITLE");
    }

    @Test
    void nullScopeDefaultsToAll() {
        when(gateway.searchDocuments(eq(workspaceId), eq("test"), eq("ALL"), any(Integer.class), any()))
                .thenReturn(List.of());

        Map<String, Object> result = service.findCandidates(workspaceId, "test", null, null, identity);

        assertThat(result.get("scope")).isEqualTo("ALL");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void blankQueryRejected(String query) {
        assertThatThrownBy(() -> service.findCandidates(workspaceId, query, null, null, identity))
                .isInstanceOf(McpToolException.class)
                .extracting("code")
                .isEqualTo(McpToolErrorCode.INVALID_DOCUMENT_QUERY);
    }

    @Test
    void queryExceedingMaxLengthRejected() {
        String longQuery = "a".repeat(501);
        assertThatThrownBy(() -> service.findCandidates(workspaceId, longQuery, null, null, identity))
                .isInstanceOf(McpToolException.class)
                .extracting("code")
                .isEqualTo(McpToolErrorCode.INVALID_DOCUMENT_QUERY);
    }

    @Test
    void queryAtExactMaxLengthAccepted() {
        String maxQuery = "a".repeat(500);
        when(gateway.searchDocuments(eq(workspaceId), eq(maxQuery), eq("ALL"), any(Integer.class), any()))
                .thenReturn(List.of());

        Map<String, Object> result = service.findCandidates(workspaceId, maxQuery, null, null, identity);

        assertThat(result.get("query")).isEqualTo(maxQuery);
    }

    @Test
    void unicodeQueryCodePointsCounted() {
        // Each emoji is 1 code point but 2 Java chars. 250 emojis = 250 code points, within limit.
        String emojiQuery = "\uD83D\uDE00".repeat(250);
        when(gateway.searchDocuments(eq(workspaceId), eq(emojiQuery), eq("ALL"), any(Integer.class), any()))
                .thenReturn(List.of());

        Map<String, Object> result = service.findCandidates(workspaceId, emojiQuery, null, null, identity);

        assertThat(result.get("query")).isEqualTo(emojiQuery);
    }

    @Test
    void coreUnavailablePropagated() {
        when(gateway.searchDocuments(eq(workspaceId), eq("test"), eq("ALL"), any(Integer.class), any()))
                .thenThrow(new McpToolException(McpToolErrorCode.CORE_UNAVAILABLE, "unavailable"));

        assertThatThrownBy(() -> service.findCandidates(workspaceId, "test", null, null, identity))
                .isInstanceOf(McpToolException.class)
                .extracting("code")
                .isEqualTo(McpToolErrorCode.CORE_UNAVAILABLE);
    }

    @Test
    void permissionDeniedPropagated() {
        when(gateway.searchDocuments(eq(workspaceId), eq("test"), eq("ALL"), any(Integer.class), any()))
                .thenThrow(new McpToolException(McpToolErrorCode.PERMISSION_DENIED, "denied"));

        assertThatThrownBy(() -> service.findCandidates(workspaceId, "test", null, null, identity))
                .isInstanceOf(McpToolException.class)
                .extracting("code")
                .isEqualTo(McpToolErrorCode.PERMISSION_DENIED);
    }

    @Test
    void queryIsTrimmed() {
        when(gateway.searchDocuments(eq(workspaceId), eq("trimmed"), eq("ALL"), any(Integer.class), any()))
                .thenReturn(List.of());

        Map<String, Object> result = service.findCandidates(workspaceId, "  trimmed  ", null, null, identity);

        assertThat(result.get("query")).isEqualTo("trimmed");
    }

    @Test
    void nullUpdatedAtOmitted() {
        UUID documentId = UUID.randomUUID();
        when(gateway.searchDocuments(eq(workspaceId), eq("test"), eq("ALL"), any(Integer.class), any()))
                .thenReturn(List.of(
                        new KnowledgeCoreGateway.SearchCandidate(
                                "DOCUMENT_TITLE", documentId, "Doc", null, null, null
                        )
                ));

        Map<String, Object> result = service.findCandidates(workspaceId, "test", null, null, identity);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) result.get("candidates");
        assertThat(candidates.get(0)).doesNotContainKey("updatedAt");
    }

    @Test
    void nullDocumentTitlePreserved() {
        UUID documentId = UUID.randomUUID();
        when(gateway.searchDocuments(eq(workspaceId), eq("test"), eq("ALL"), any(Integer.class), any()))
                .thenReturn(List.of(
                        new KnowledgeCoreGateway.SearchCandidate(
                                "DOCUMENT_TITLE", documentId, null, null, null, Instant.now()
                        )
                ));

        Map<String, Object> result = service.findCandidates(workspaceId, "test", null, null, identity);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) result.get("candidates");
        assertThat(candidates.get(0).get("documentTitle")).isNull();
    }

    private KnowledgeCoreGateway.SearchCandidate candidate(
            String type, UUID documentId, String title, UUID blockId, String snippet) {
        return new KnowledgeCoreGateway.SearchCandidate(
                type, documentId, title, blockId, snippet, Instant.now()
        );
    }
}
