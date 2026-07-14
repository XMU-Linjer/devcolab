package com.devcollab.knowledgecore.search.api;

import com.devcollab.knowledgecore.common.outbox.application.OutboxRelayResult;
import com.devcollab.knowledgecore.common.outbox.application.OutboxRelayService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfSystemProperty(
        named = "devcollab.search.benchmark",
        matches = "true"
)
abstract class AbstractSearchBenchmarkIT {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired(required = false)
    protected OutboxRelayService outboxRelayService;

    @Test
    void shouldRunSearchBenchmark() throws Exception {
        BenchmarkOptions options = BenchmarkOptions.fromSystemProperties();
        String token = registerAndGetAccessToken();
        String workspaceId = createWorkspace(
                token,
                engineName() + " benchmark " + options.totalBlocks()
        ).get("id").asText();

        long seedStartedAt = System.nanoTime();
        seedBenchmarkData(token, workspaceId, options);
        long seedMs = elapsedMs(seedStartedAt);

        OutboxRelayResult relayResult = relayOutboxIfNeeded();
        refreshSearchIndexIfNeeded();

        for (SearchScenario scenario : SearchScenario.all()) {
            runScenario(token, workspaceId, scenario, options);
        }

        System.out.printf(
                "SEARCH_DATASET engine=%s blocks=%d blocksPerDocument=%d documents=%d seedMs=%d publishedEvents=%d failedEvents=%d%n",
                engineName(),
                options.totalBlocks(),
                options.blocksPerDocument(),
                documentCount(options),
                seedMs,
                relayResult == null ? 0 : relayResult.published(),
                relayResult == null ? 0 : relayResult.failed()
        );
    }

    protected abstract String engineName();

    protected OutboxRelayResult relayOutboxIfNeeded() {
        return null;
    }

    protected void refreshSearchIndexIfNeeded() {
    }

    private void runScenario(
            String token,
            String workspaceId,
            SearchScenario scenario,
            BenchmarkOptions options
    ) throws Exception {
        for (int i = 0; i < options.warmupIterations(); i++) {
            search(token, workspaceId, scenario.keyword());
        }

        List<Long> durations = new ArrayList<>(options.measureIterations());
        int hits = 0;
        int titleHits = 0;
        int contentHits = 0;

        for (int i = 0; i < options.measureIterations(); i++) {
            long startedAt = System.nanoTime();
            JsonNode result = search(token, workspaceId, scenario.keyword());
            durations.add(elapsedMs(startedAt));
            hits = result.size();
            titleHits = countByType(result, "DOCUMENT_TITLE");
            contentHits = countByType(result, "BLOCK_CONTENT");
        }

        assertThat(hits).isGreaterThanOrEqualTo(scenario.minimumHits());

        durations.sort(Comparator.naturalOrder());
        System.out.printf(
                "SEARCH_BENCHMARK engine=%s blocks=%d scenario=%s keyword=%s hits=%d titleHits=%d contentHits=%d iterations=%d warmup=%d p50Ms=%d p95Ms=%d maxMs=%d%n",
                engineName(),
                options.totalBlocks(),
                scenario.name(),
                scenario.keyword(),
                hits,
                titleHits,
                contentHits,
                options.measureIterations(),
                options.warmupIterations(),
                percentile(durations, 50),
                percentile(durations, 95),
                durations.getLast()
        );
    }

    private void seedBenchmarkData(
            String token,
            String workspaceId,
            BenchmarkOptions options
    ) throws Exception {
        int createdBlocks = 0;
        for (int documentIndex = 0;
             createdBlocks < options.totalBlocks();
             documentIndex++) {
            String title = documentTitle(documentIndex);
            String documentId = createDocument(token, workspaceId, title)
                    .get("id")
                    .asText();

            for (int blockOffset = 0;
                 blockOffset < options.blocksPerDocument()
                         && createdBlocks < options.totalBlocks();
                 blockOffset++) {
                createBlock(
                        token,
                        documentId,
                        blockText(createdBlocks)
                );
                createdBlocks++;
            }
        }
    }

    private String documentTitle(int documentIndex) {
        if (documentIndex % 5 == 0) {
            return "API Benchmark Document " + documentIndex;
        }
        if (documentIndex % 7 == 0) {
            return "Agent Review Benchmark " + documentIndex;
        }
        return "Benchmark Document " + documentIndex;
    }

    private String blockText(int blockIndex) {
        return switch (blockIndex % 10) {
            case 0 -> "api benchmark block " + blockIndex
                    + " defines order api contract and response fields";
            case 1 -> "idempotency benchmark block " + blockIndex
                    + " explains retry safety and idempotency key";
            case 2 -> "csrf benchmark block " + blockIndex
                    + " explains csrf header and refresh cookie";
            case 3 -> "agent benchmark block " + blockIndex
                    + " describes agent review evidence retrieval";
            default -> "general benchmark block " + blockIndex
                    + " contains workspace document collaboration notes";
        };
    }

    private int documentCount(BenchmarkOptions options) {
        return (int) Math.ceil(
                options.totalBlocks() / (double) options.blocksPerDocument()
        );
    }

    private JsonNode search(
            String token,
            String workspaceId,
            String keyword
    ) throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/api/v1/workspaces/{id}/search", workspaceId)
                                .queryParam("keyword", keyword)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                )
                .andExpect(status().isOk())
                .andReturn();
        return responseJson(result);
    }

    private JsonNode createWorkspace(String token, String name)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/workspaces")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new WorkspaceBody(name)
                        )))
                .andExpect(status().isCreated())
                .andReturn();
        return responseJson(result);
    }

    private JsonNode createDocument(
            String token,
            String workspaceId,
            String title
    ) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/v1/workspaces/{id}/documents", workspaceId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        new DocumentBody(null, title)
                                ))
                )
                .andExpect(status().isCreated())
                .andReturn();
        return responseJson(result);
    }

    private void createBlock(
            String token,
            String documentId,
            String text
    ) throws Exception {
        mockMvc.perform(
                        post("/api/v1/documents/{id}/blocks", documentId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        new BlockBody(
                                                "PARAGRAPH",
                                                new BlockContentBody(text)
                                        )
                                ))
                )
                .andExpect(status().isCreated());
    }

    private String registerAndGetAccessToken() throws Exception {
        String username = "bench_" + engineName() + "_"
                + UUID.randomUUID().toString().substring(0, 8);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterBody(
                                        username,
                                        "Search Benchmark",
                                        "password123"
                                )
                        )))
                .andExpect(status().isCreated())
                .andReturn();
        return responseJson(result).get("accessToken").asText();
    }

    protected JsonNode responseJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    private int countByType(JsonNode hits, String type) {
        int count = 0;
        for (JsonNode hit : hits) {
            if (type.equals(hit.get("type").asText())) {
                count++;
            }
        }
        return count;
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private static long percentile(List<Long> sortedDurations, int percentile) {
        int index = (int) Math.ceil(
                sortedDurations.size() * (percentile / 100.0)
        ) - 1;
        return sortedDurations.get(Math.max(0, index));
    }

    private record BenchmarkOptions(
            int totalBlocks,
            int blocksPerDocument,
            int warmupIterations,
            int measureIterations
    ) {
        private static BenchmarkOptions fromSystemProperties() {
            return new BenchmarkOptions(
                    intProperty("devcollab.search.benchmark.blocks", 100),
                    intProperty(
                            "devcollab.search.benchmark.blocks-per-document",
                            10
                    ),
                    intProperty("devcollab.search.benchmark.warmup", 3),
                    intProperty("devcollab.search.benchmark.iterations", 10)
            );
        }

        private static int intProperty(String name, int defaultValue) {
            return Integer.parseInt(System.getProperty(
                    name,
                    String.valueOf(defaultValue)
            ));
        }
    }

    private record SearchScenario(
            String name,
            String keyword,
            int minimumHits
    ) {
        private static List<SearchScenario> all() {
            return List.of(
                    new SearchScenario("api", "api", 1),
                    new SearchScenario("idempotency", "idempotency", 1),
                    new SearchScenario("csrf", "csrf", 1),
                    new SearchScenario("agent", "agent", 1),
                    new SearchScenario("no-hit", "not-exists-keyword", 0)
            );
        }
    }

    private record RegisterBody(
            String username,
            String displayName,
            String password
    ) {
    }

    private record WorkspaceBody(String name) {
    }

    private record DocumentBody(
            String parentDocumentId,
            String title
    ) {
    }

    private record BlockBody(
            String type,
            BlockContentBody content
    ) {
    }

    private record BlockContentBody(String text) {
    }
}
