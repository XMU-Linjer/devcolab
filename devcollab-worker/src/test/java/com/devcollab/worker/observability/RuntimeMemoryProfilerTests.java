package com.devcollab.worker.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeMemoryProfilerTests {

    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path temp;

    @Test
    void disabledProfilerCreatesNoFilesOrThreads() throws Exception {
        RuntimeMemoryProfiler profiler = profiler(false, "disabled", temp, 8);

        assertThat(profiler.enabled()).isFalse();
        assertThat(profiler.stage("JOB", null, null, null, null))
                .isSameAs(RuntimeMemoryProfiler.Stage.noop());
        profiler.close();

        assertThat(Files.list(temp)).isEmpty();
    }

    @Test
    void enabledProfilerWritesValidJvmSample() throws Exception {
        RuntimeMemoryProfiler profiler = profiler(true, "sample-run", temp, 8);
        Thread.sleep(750);
        profiler.close();

        JsonNode sample = firstJson("*-samples.jsonl", "sample-run");
        assertThat(sample.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(sample.path("recordType").asText()).isEqualTo("sample");
        assertThat(sample.path("heapUsedBytes").asLong()).isPositive();
        assertThat(sample.path("heapCommittedBytes").asLong())
                .isGreaterThanOrEqualTo(sample.path("heapUsedBytes").asLong());
        assertThat(sample.has("directBufferUsedBytes")).isTrue();
        assertThat(sample.path("threadCount").asInt()).isPositive();
    }

    @Test
    void stageWritesStartedAndCompletedWithBoundedAttributes() throws Exception {
        RuntimeMemoryProfiler profiler = profiler(true, "stage-run", temp, 8);
        try (var stage = profiler.stage("FILE_SCAN", "job", "repo", "sha", null)) {
            stage.attribute("fileCount", 3);
        }
        profiler.close();

        List<String> lines = lines("*-events.jsonl", "stage-run");
        assertThat(lines).hasSize(2);
        assertThat(JSON.readTree(lines.get(0)).path("eventType").asText())
                .isEqualTo("STARTED");
        JsonNode completed = JSON.readTree(lines.get(1));
        assertThat(completed.path("eventType").asText()).isEqualTo("COMPLETED");
        assertThat(completed.path("attributes").path("fileCount").asInt()).isEqualTo(3);
    }

    @Test
    void nestedStageRecordsParentExecutionId() throws Exception {
        RuntimeMemoryProfiler profiler = profiler(true, "nested-run", temp, 16);
        try (var outer = profiler.stage("JOB", "job", "repo", null, null)) {
            try (var ignored = profiler.stage("FILE_SCAN", "job", "repo", null, null)) {
                // Nested stage only.
            }
        }
        profiler.close();

        List<JsonNode> events = lines("*-events.jsonl", "nested-run").stream()
                .map(line -> {
                    try {
                        return JSON.readTree(line);
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                })
                .toList();
        JsonNode outer = events.stream()
                .filter(event -> "JOB".equals(event.path("stage").asText()))
                .findFirst().orElseThrow();
        JsonNode child = events.stream()
                .filter(event -> "FILE_SCAN".equals(event.path("stage").asText()))
                .findFirst().orElseThrow();
        assertThat(child.path("parentStageExecutionId").asText())
                .isEqualTo(outer.path("stageExecutionId").asText());
    }

    @Test
    void failedStageRecordsOnlyExceptionType() throws Exception {
        RuntimeMemoryProfiler profiler = profiler(true, "failed-run", temp, 8);
        IllegalStateException failure = new IllegalStateException("secret detail");
        try (var stage = profiler.stage("CODE_GRAPH", "job", "repo", null, null)) {
            stage.failed(failure);
        }
        profiler.close();

        JsonNode failed = JSON.readTree(lines("*-events.jsonl", "failed-run").get(1));
        assertThat(failed.path("eventType").asText()).isEqualTo("FAILED");
        assertThat(failed.path("errorType").asText()).isEqualTo("IllegalStateException");
        assertThat(failed.toString()).doesNotContain("secret detail");
    }

    @Test
    void attributesRejectObjectsOversizeValuesAndTooManyKeys() {
        RuntimeMemoryProfiler profiler = profiler(true, "attribute-run", temp, 8);
        RuntimeMemoryProfiler.Stage stage = profiler.stage(
                "JOB", null, null, null, null
        );

        assertThatThrownBy(() -> stage.attribute("payload", new Object()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stage.attribute("prompt", "x".repeat(257)))
                .isInstanceOf(IllegalArgumentException.class);
        for (int index = 0; index < 24; index++) {
            stage.attribute("value" + index, index);
        }
        assertThatThrownBy(() -> stage.attribute("overflow", 1))
                .isInstanceOf(IllegalArgumentException.class);
        stage.close();
        profiler.close();
    }

    @Test
    void fullQueueDropsWithoutBlockingBusinessThread() {
        RuntimeMemoryProfiler profiler = profiler(true, "drop-run", temp, 1);
        long started = System.nanoTime();
        for (int index = 0; index < 20_000; index++) {
            try (var ignored = profiler.stage("JOB", null, null, null, null)) {
                // Intentionally empty.
            }
        }
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        profiler.close();

        assertThat(elapsedMs).isLessThan(5_000);
        assertThat(profiler.droppedRecordCount()).isPositive();
    }

    @Test
    void invalidRunIdIsRejected() {
        assertThatThrownBy(() -> profiler(true, "../escape", temp, 8))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> profiler(true, "a/b", temp, 8))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void outputInitializationFailureDisablesProfilerWithoutFailingStartup()
            throws Exception {
        Path file = temp.resolve("not-a-directory");
        Files.writeString(file, "occupied");
        RuntimeMemoryProfiler profiler = profiler(true, "safe-run", file, 8);

        assertThat(profiler.enabled()).isFalse();
        profiler.close();
    }

    private RuntimeMemoryProfiler profiler(
            boolean enabled, String runId, Path output, int capacity
    ) {
        return new RuntimeMemoryProfiler(
                enabled, runId, output, 500, capacity, "test-worker"
        );
    }

    private JsonNode firstJson(String pattern, String runId) throws Exception {
        return JSON.readTree(lines(pattern, runId).getFirst());
    }

    private List<String> lines(String pattern, String runId) throws Exception {
        try (var paths = Files.newDirectoryStream(temp.resolve(runId), pattern)) {
            return Files.readAllLines(paths.iterator().next());
        }
    }
}
