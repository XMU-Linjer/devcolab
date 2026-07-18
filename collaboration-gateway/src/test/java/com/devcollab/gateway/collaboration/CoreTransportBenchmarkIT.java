package com.devcollab.gateway.collaboration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Manual, real-service HTTP/gRPC comparison. It is intentionally named *IT so
 * the ordinary unit-test suite never depends on PostgreSQL or a running Core.
 */
class CoreTransportBenchmarkIT {

    private static final ObjectMapper JSON =
            new ObjectMapper().findAndRegisterModules();

    @Test
    void benchmarkEquivalentCoreOperations() throws Exception {
        String baseUrl = property("core.http", "http://localhost:8080");
        String grpcHost = property("core.grpc.host", "127.0.0.1");
        int grpcPort = integerProperty("core.grpc.port", 9090);
        int warmup = integerProperty("benchmark.warmup", 30);
        int iterations = integerProperty("benchmark.iterations", 200);
        int rounds = integerProperty("benchmark.rounds", 3);
        Path output = resolveOutput(Path.of(property(
                "benchmark.output",
                "tools/benchmark/results/core-transport-comparison.json"
        )));

        Api api = new Api(baseUrl);
        Fixture fixture = api.seed();
        GatewayProperties gatewayProperties = new GatewayProperties(
                baseUrl,
                Duration.ofMinutes(1),
                Duration.ofMinutes(1),
                Duration.ofMinutes(5)
        );
        HttpCoreDocumentAccessVerifier httpAccess =
                new HttpCoreDocumentAccessVerifier(
                        WebClient.builder(),
                        gatewayProperties
                );
        HttpCoreDocumentOperationClient httpOperations =
                new HttpCoreDocumentOperationClient(
                        WebClient.builder(),
                        gatewayProperties
                );
        CoreGrpcClientProperties grpcProperties =
                new CoreGrpcClientProperties(
                        grpcHost,
                        grpcPort,
                        true,
                        Duration.ofSeconds(3),
                        1_048_576,
                        Duration.ofSeconds(3)
                );

        Map<String, Samples> samples = new LinkedHashMap<>();
        samples.put("access.http", new Samples());
        samples.put("access.grpc", new Samples());
        samples.put("catch-up.http", new Samples());
        samples.put("catch-up.grpc", new Samples());
        samples.put("apply.http", new Samples());
        samples.put("apply.grpc", new Samples());

        try (CoreGrpcChannel grpcChannel = new CoreGrpcChannel(grpcProperties)) {
            CoreGrpcClientMetrics metrics = new CoreGrpcClientMetrics(
                    new SimpleMeterRegistry(),
                    grpcChannel
            );
            GrpcCoreDocumentAccessVerifier grpcAccess =
                    new GrpcCoreDocumentAccessVerifier(
                            grpcChannel,
                            grpcProperties,
                            metrics
                    );
            GrpcCoreDocumentOperationClient grpcOperations =
                    new GrpcCoreDocumentOperationClient(
                            grpcChannel,
                            grpcProperties,
                            metrics
                    );

            // Seed an identical catch-up result that both transports read.
            assertApplied(httpOperations.apply(
                    fixture.readDocumentId(),
                    fixture.readBlockId(),
                    UUID.randomUUID(),
                    fixture.accessToken(),
                    "UPDATE_TEXT",
                    "catch-up fixture",
                    fixture.readBlockVersion(),
                    null,
                    null
            ));

            long[] versions = {
                    fixture.httpBlockVersion(),
                    fixture.grpcBlockVersion()
            };
            for (int round = 0; round < rounds; round++) {
                for (int index = 0; index < warmup; index++) {
                    invokeAlternating(index,
                            () -> access(httpAccess, fixture),
                            () -> access(grpcAccess, fixture));
                    invokeAlternating(index,
                            () -> catchUp(httpOperations, fixture),
                            () -> catchUp(grpcOperations, fixture));
                    if ((round + index) % 2 == 0) {
                        versions[0] = warmupApply(
                                httpOperations,
                                fixture.httpDocumentId(),
                                fixture.httpBlockId(),
                                fixture.accessToken(),
                                "http-warmup-" + round + "-" + index,
                                versions[0]
                        );
                        versions[1] = warmupApply(
                                grpcOperations,
                                fixture.grpcDocumentId(),
                                fixture.grpcBlockId(),
                                fixture.accessToken(),
                                "grpc-warmup-" + round + "-" + index,
                                versions[1]
                        );
                    } else {
                        versions[1] = warmupApply(
                                grpcOperations,
                                fixture.grpcDocumentId(),
                                fixture.grpcBlockId(),
                                fixture.accessToken(),
                                "grpc-warmup-" + round + "-" + index,
                                versions[1]
                        );
                        versions[0] = warmupApply(
                                httpOperations,
                                fixture.httpDocumentId(),
                                fixture.httpBlockId(),
                                fixture.accessToken(),
                                "http-warmup-" + round + "-" + index,
                                versions[0]
                        );
                    }
                }
                for (int index = 0; index < iterations; index++) {
                    boolean httpFirst = (round + index) % 2 == 0;
                    String httpText = "http-" + round + "-" + index;
                    String grpcText = "grpc-" + round + "-" + index;
                    measurePair(
                            httpFirst,
                            samples.get("access.http"),
                            () -> access(httpAccess, fixture),
                            samples.get("access.grpc"),
                            () -> access(grpcAccess, fixture)
                    );
                    measurePair(
                            httpFirst,
                            samples.get("catch-up.http"),
                            () -> catchUp(httpOperations, fixture),
                            samples.get("catch-up.grpc"),
                            () -> catchUp(grpcOperations, fixture)
                    );

                    Supplier<CoreDocumentOperationClient
                            .CoreDocumentOperationResult> httpApply = () ->
                            httpOperations.apply(
                                    fixture.httpDocumentId(),
                                    fixture.httpBlockId(),
                                    UUID.randomUUID(),
                                    fixture.accessToken(),
                                    "UPDATE_TEXT",
                                    httpText,
                                    versions[0],
                                    null,
                                    null
                            );
                    Supplier<CoreDocumentOperationClient
                            .CoreDocumentOperationResult> grpcApply = () ->
                            grpcOperations.apply(
                                    fixture.grpcDocumentId(),
                                    fixture.grpcBlockId(),
                                    UUID.randomUUID(),
                                    fixture.accessToken(),
                                    "UPDATE_TEXT",
                                    grpcText,
                                    versions[1],
                                    null,
                                    null
                            );
                    if (httpFirst) {
                        versions[0] = applyAndVersion(
                                samples.get("apply.http"), httpApply
                        );
                        versions[1] = applyAndVersion(
                                samples.get("apply.grpc"), grpcApply
                        );
                    } else {
                        versions[1] = applyAndVersion(
                                samples.get("apply.grpc"), grpcApply
                        );
                        versions[0] = applyAndVersion(
                                samples.get("apply.http"), httpApply
                        );
                    }
                }
            }
        }

        Map<String, Object> report = report(
                baseUrl,
                grpcHost + ":" + grpcPort,
                warmup,
                iterations,
                rounds,
                samples
        );
        Files.createDirectories(output.toAbsolutePath().getParent());
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
        Path markdown = output.resolveSibling(
                output.getFileName().toString().replace(".json", ".md")
        );
        Files.writeString(markdown, markdown(report), StandardCharsets.UTF_8);
        System.out.println("[core-transport-benchmark] wrote " + output);
        System.out.println(markdown(report));
    }

    private Map<String, Object> report(
            String httpTarget,
            String grpcTarget,
            int warmup,
            int iterations,
            int rounds,
            Map<String, Samples> samples
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        samples.forEach((name, values) -> result.put(name, values.stats()));
        Map<String, Object> ratios = new LinkedHashMap<>();
        boolean localPerformanceGate = true;
        for (String operation : List.of("access", "catch-up", "apply")) {
            double httpP95 = samples.get(operation + ".http").stats().p95Ms();
            double grpcP95 = samples.get(operation + ".grpc").stats().p95Ms();
            double ratio = grpcP95 / httpP95;
            ratios.put(operation + ".grpcToHttpP95", round(ratio));
            localPerformanceGate &= ratio <= 1.10;
        }
        return Map.of(
                "generatedAt", Instant.now().toString(),
                "environment", Map.of(
                        "httpTarget", httpTarget,
                        "grpcTarget", grpcTarget,
                        "javaVersion", System.getProperty("java.version"),
                        "warmupPerRound", warmup,
                        "iterationsPerRound", iterations,
                        "rounds", rounds,
                        "execution", "single-threaded alternating A/B"
                ),
                "results", result,
                "ratios", ratios,
                "decision", Map.of(
                        "localPerformanceGatePassed", localPerformanceGate,
                        "httpClientAction", "KEEP_PENDING_STAGING_EVIDENCE",
                        "threshold", "every gRPC p95 <= 110% of HTTP p95",
                        "remainingEvidence", List.of(
                                "three clean staging runs",
                                "zero semantic mismatches",
                                "24h canary without gRPC status or channel alerts",
                                "documented rollback through core-transport=http"
                        )
                )
        );
    }

    @SuppressWarnings("unchecked")
    private String markdown(Map<String, Object> report) {
        Map<String, Stats> results = (Map<String, Stats>) report.get("results");
        StringBuilder text = new StringBuilder()
                .append("# Core HTTP/gRPC 同语义性能对比\n\n")
                .append("| 语义 | 协议 | avg(ms) | p95(ms) | p99(ms) | QPS |\n")
                .append("|---|---:|---:|---:|---:|---:|\n");
        results.forEach((name, stats) -> {
            String[] parts = name.split("\\.");
            text.append("| ").append(parts[0]).append(" | ")
                    .append(parts[1]).append(" | ")
                    .append(stats.avgMs()).append(" | ")
                    .append(stats.p95Ms()).append(" | ")
                    .append(stats.p99Ms()).append(" | ")
                    .append(stats.qps()).append(" |\n");
        });
        text.append("\n> 本地数据只决定是否进入预发布验证，不直接授权删除 HTTP Client。\n");
        return text.toString();
    }

    private void access(CoreDocumentAccessVerifier verifier, Fixture fixture) {
        verifier.verifyCanAccess(
                fixture.readDocumentId(),
                fixture.accessToken()
        );
    }

    private void catchUp(
            CoreDocumentOperationClient client,
            Fixture fixture
    ) {
        var result = client.listAfter(
                fixture.readDocumentId(),
                fixture.accessToken(),
                0,
                20
        );
        assertThat(result.operations()).isNotEmpty();
    }

    private void invokeAlternating(
            int index,
            Runnable http,
            Runnable grpc
    ) {
        if (index % 2 == 0) {
            http.run();
            grpc.run();
        } else {
            grpc.run();
            http.run();
        }
    }

    private void measurePair(
            boolean httpFirst,
            Samples httpSamples,
            Runnable http,
            Samples grpcSamples,
            Runnable grpc
    ) {
        if (httpFirst) {
            timed(httpSamples, () -> run(http));
            timed(grpcSamples, () -> run(grpc));
        } else {
            timed(grpcSamples, () -> run(grpc));
            timed(httpSamples, () -> run(http));
        }
    }

    private Boolean run(Runnable action) {
        action.run();
        return Boolean.TRUE;
    }

    private <T> T timed(Samples samples, Supplier<T> invocation) {
        long started = System.nanoTime();
        try {
            return invocation.get();
        } finally {
            samples.add(System.nanoTime() - started);
        }
    }

    private void assertApplied(
            CoreDocumentOperationClient.CoreDocumentOperationResult result
    ) {
        assertThat(result.status()).isEqualTo("APPLIED");
        assertThat(result.block()).isNotNull();
    }

    private long applyAndVersion(
            Samples samples,
            Supplier<CoreDocumentOperationClient.CoreDocumentOperationResult>
                    invocation
    ) {
        var result = timed(samples, invocation);
        assertApplied(result);
        return result.block().version();
    }

    private long warmupApply(
            CoreDocumentOperationClient client,
            UUID documentId,
            UUID blockId,
            String accessToken,
            String text,
            long expectedVersion
    ) {
        var result = client.apply(
                documentId,
                blockId,
                UUID.randomUUID(),
                accessToken,
                "UPDATE_TEXT",
                text,
                expectedVersion,
                null,
                null
        );
        assertApplied(result);
        return result.block().version();
    }

    private static String property(String name, String fallback) {
        return System.getProperty(name, fallback);
    }

    private static int integerProperty(String name, int fallback) {
        return Integer.parseInt(System.getProperty(name, String.valueOf(fallback)));
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static Path resolveOutput(Path configured) {
        if (configured.isAbsolute()) {
            return configured.normalize();
        }
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve(".git"))) {
                return current.resolve(configured).normalize();
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate repository root");
    }

    private static final class Samples {
        private final List<Long> nanos = new ArrayList<>();

        void add(long value) {
            nanos.add(value);
        }

        Stats stats() {
            List<Long> sorted = nanos.stream().sorted().toList();
            double total = sorted.stream().mapToLong(Long::longValue).sum();
            double seconds = total / 1_000_000_000.0;
            return new Stats(
                    sorted.size(),
                    round(total / sorted.size() / 1_000_000.0),
                    milliseconds(percentile(sorted, 0.50)),
                    milliseconds(percentile(sorted, 0.95)),
                    milliseconds(percentile(sorted, 0.99)),
                    round(sorted.size() / seconds)
            );
        }

        private long percentile(List<Long> sorted, double percentile) {
            int index = (int) Math.ceil(percentile * sorted.size()) - 1;
            return sorted.get(Math.max(0, index));
        }

        private double milliseconds(long value) {
            return round(value / 1_000_000.0);
        }
    }

    private record Stats(
            int samples,
            double avgMs,
            double p50Ms,
            double p95Ms,
            double p99Ms,
            double qps
    ) {
    }

    private record Fixture(
            String accessToken,
            UUID readDocumentId,
            UUID readBlockId,
            long readBlockVersion,
            UUID httpDocumentId,
            UUID httpBlockId,
            long httpBlockVersion,
            UUID grpcDocumentId,
            UUID grpcBlockId,
            long grpcBlockVersion
    ) {
    }

    private static final class Api {
        private final String baseUrl;
        private final HttpClient client = HttpClient.newHttpClient();
        private String token;

        private Api(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        Fixture seed() throws Exception {
            String suffix = String.valueOf(System.currentTimeMillis());
            JsonNode auth = post("/api/v1/auth/register", Map.of(
                    "username", "transport_bench_" + suffix,
                    "displayName", "Transport Benchmark " + suffix,
                    "password", "Benchmark@123456"
            ));
            token = auth.path("accessToken").asText();
            JsonNode workspace = post("/api/v1/workspaces", Map.of(
                    "name", "Transport Benchmark " + suffix
            ));
            UUID workspaceId = uuid(workspace, "id");
            BlockFixture read = documentWithBlock(workspaceId, "Read Fixture");
            BlockFixture http = documentWithBlock(workspaceId, "HTTP Write Fixture");
            BlockFixture grpc = documentWithBlock(workspaceId, "gRPC Write Fixture");
            return new Fixture(
                    token,
                    read.documentId(), read.blockId(), read.version(),
                    http.documentId(), http.blockId(), http.version(),
                    grpc.documentId(), grpc.blockId(), grpc.version()
            );
        }

        private BlockFixture documentWithBlock(UUID workspaceId, String title)
                throws Exception {
            JsonNode document = post(
                    "/api/v1/workspaces/" + workspaceId + "/documents",
                    Map.of("title", title, "documentType", "REQUIREMENT")
            );
            UUID documentId = uuid(document, "id");
            JsonNode block = post(
                    "/api/v1/documents/" + documentId + "/blocks",
                    Map.of(
                            "type", "PARAGRAPH",
                            "content", Map.of("text", "benchmark fixture")
                    )
            );
            return new BlockFixture(
                    documentId,
                    uuid(block, "id"),
                    block.path("version").asLong()
            );
        }

        private JsonNode post(String path, Object body) throws Exception {
            HttpRequest.Builder request = HttpRequest.newBuilder(
                            URI.create(baseUrl + path)
                    )
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            JSON.writeValueAsString(body)
                    ));
            if (token != null) {
                request.header("Authorization", "Bearer " + token);
            }
            HttpResponse<String> response = client.send(
                    request.build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException(
                        "Seed request failed status=" + response.statusCode()
                                + " path=" + path + " body=" + response.body()
                );
            }
            return JSON.readTree(response.body());
        }

        private UUID uuid(JsonNode node, String field) {
            return UUID.fromString(node.path(field).asText());
        }
    }

    private record BlockFixture(UUID documentId, UUID blockId, long version) {
    }
}
