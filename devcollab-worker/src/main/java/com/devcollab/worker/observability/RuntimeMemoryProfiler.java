package com.devcollab.worker.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

@Component
public class RuntimeMemoryProfiler implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeMemoryProfiler.class);
    private static final Pattern SAFE_RUN_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final int ATTRIBUTE_LIMIT = 24;
    private static final int ATTRIBUTE_STRING_LIMIT = 256;
    private static final int SCHEMA_VERSION = 1;
    private static final Entry STOP = new Entry(false, "");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ArrayBlockingQueue<Entry> queue;
    private final AtomicBoolean active = new AtomicBoolean();
    private final AtomicLong samplesWritten = new AtomicLong();
    private final AtomicLong eventsWritten = new AtomicLong();
    private final AtomicLong droppedRecords = new AtomicLong();
    private final AtomicLong writeErrors = new AtomicLong();
    private final ThreadLocal<Stage> currentStage = new ThreadLocal<>();
    private final String runId;
    private final String service;
    private final String instanceId;
    private final long pid;
    private final long intervalMs;
    private final Path sampleFile;
    private final Path eventFile;
    private Thread samplerThread;
    private Thread writerThread;

    @Autowired
    public RuntimeMemoryProfiler(
            @Value("${devcollab.memory-profile.enabled:false}") boolean enabled,
            @Value("${devcollab.memory-profile.run-id:}") String configuredRunId,
            @Value("${devcollab.memory-profile.output-dir:.data/memory-profile}") String outputDir,
            @Value("${devcollab.memory-profile.interval-ms:1000}") long configuredIntervalMs,
            @Value("${devcollab.memory-profile.queue-capacity:1024}") int queueCapacity,
            @Value("${spring.application.name:devcollab-worker}") String service
    ) {
        this(enabled, configuredRunId, Path.of(outputDir), configuredIntervalMs, queueCapacity,
                service);
    }

    RuntimeMemoryProfiler(
            boolean enabled,
            String configuredRunId,
            Path outputDir,
            long configuredIntervalMs,
            int queueCapacity,
            String service
    ) {
        this.service = safeService(service);
        this.pid = ProcessHandle.current().pid();
        this.instanceId = this.service + "-" + pid + "-" + UUID.randomUUID().toString().substring(0, 8);
        this.intervalMs = configuredIntervalMs >= 500 ? configuredIntervalMs : 1000;
        this.queue = new ArrayBlockingQueue<>(Math.max(1, Math.min(queueCapacity, 65_536)));
        this.runId = enabled ? safeRunId(configuredRunId) : "disabled";
        Path runDirectory = outputDir.toAbsolutePath().normalize().resolve(this.runId).normalize();
        Path root = outputDir.toAbsolutePath().normalize();
        if (!runDirectory.startsWith(root)) {
            throw new IllegalArgumentException("Memory profile run directory escapes output root");
        }
        this.sampleFile = runDirectory.resolve(this.service + "-" + pid + "-samples.jsonl");
        this.eventFile = runDirectory.resolve(this.service + "-" + pid + "-events.jsonl");
        if (enabled) start(runDirectory);
    }

    public boolean enabled() {
        return active.get();
    }

    public Stage stage(
            String stage,
            String jobId,
            String repositoryId,
            String revision,
            String unitId
    ) {
        if (!active.get()) return Stage.noop();
        Stage parent = currentStage.get();
        Stage created = new Stage(
                this, safeStage(stage), jobId, repositoryId, revision, unitId, parent
        );
        currentStage.set(created);
        return created;
    }

    long droppedRecordCount() {
        return droppedRecords.get();
    }

    Map<String, Object> runtimeSample() {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        MemoryUsage nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        BufferPoolMXBean direct = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)
                .stream().filter(pool -> "direct".equals(pool.getName())).findFirst().orElse(null);
        long gcCount = ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionCount)
                .filter(value -> value >= 0).sum();
        long gcTime = ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionTime)
                .filter(value -> value >= 0).sum();
        var threads = ManagementFactory.getThreadMXBean();
        var runtime = ManagementFactory.getRuntimeMXBean();
        var baseOs = ManagementFactory.getOperatingSystemMXBean();
        Long virtualMemory = null;
        Double cpuPercent = null;
        Long openFiles = null;
        if (baseOs instanceof com.sun.management.OperatingSystemMXBean os) {
            virtualMemory = nullableNonNegative(os.getCommittedVirtualMemorySize());
            double load = os.getProcessCpuLoad();
            cpuPercent = load < 0 ? null : load * 100.0;
        }
        if (baseOs instanceof com.sun.management.UnixOperatingSystemMXBean unix) {
            openFiles = nullableNonNegative(unix.getOpenFileDescriptorCount());
        }
        Long metaspace = ManagementFactory.getMemoryPoolMXBeans().stream()
                .filter(pool -> "Metaspace".equals(pool.getName()))
                .map(MemoryPoolMXBean::getUsage)
                .filter(usage -> usage != null)
                .map(MemoryUsage::getUsed)
                .findFirst().orElse(null);

        Map<String, Object> record = common("sample");
        record.put("rssBytes", linuxRssBytes());
        record.put("virtualMemoryBytes", virtualMemory);
        record.put("cpuPercent", cpuPercent);
        record.put("threadCount", threads.getThreadCount());
        record.put("peakThreadCount", threads.getPeakThreadCount());
        record.put("openFileDescriptorCount", openFiles);
        record.put("heapUsedBytes", heap.getUsed());
        record.put("heapCommittedBytes", heap.getCommitted());
        record.put("heapMaxBytes", heap.getMax());
        record.put("nonHeapUsedBytes", nonHeap.getUsed());
        record.put("metaspaceUsedBytes", metaspace);
        record.put("directBufferUsedBytes", direct == null ? null : direct.getMemoryUsed());
        record.put("directBufferCapacityBytes", direct == null ? null : direct.getTotalCapacity());
        record.put("directBufferCount", direct == null ? null : direct.getCount());
        record.put("gcCount", gcCount);
        record.put("gcTimeMs", gcTime);
        record.put("pythonAllocatedBytes", null);
        record.put("pythonPeakAllocatedBytes", null);
        record.put("processUptimeMs", runtime.getUptime());
        record.put("samplesWritten", samplesWritten.get());
        record.put("eventsWritten", eventsWritten.get());
        record.put("droppedRecords", droppedRecords.get());
        record.put("writeErrors", writeErrors.get());
        record.put("profilingOverheadWarning", droppedRecords.get() > 0);
        return record;
    }

    private void start(Path runDirectory) {
        try {
            Files.createDirectories(runDirectory);
            active.set(true);
            writerThread = new Thread(this::writerLoop, service + "-memory-profile-writer");
            samplerThread = new Thread(this::samplerLoop, service + "-memory-profile-sampler");
            writerThread.setDaemon(true);
            samplerThread.setDaemon(true);
            writerThread.start();
            samplerThread.start();
        } catch (Exception exception) {
            active.set(false);
            LOGGER.warn("Runtime memory profiling disabled because output initialization failed: {}",
                    exception.getClass().getSimpleName());
        }
    }

    private void samplerLoop() {
        while (active.get()) {
            enqueue(false, runtimeSample());
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException exception) {
                LOGGER.warn("Runtime memory sampling disabled: {}",
                        exception.getClass().getSimpleName());
                active.set(false);
            }
        }
    }

    private void writerLoop() {
        try (BufferedWriter samples = writer(sampleFile);
             BufferedWriter events = writer(eventFile)) {
            while (active.get() || !queue.isEmpty()) {
                Entry entry = queue.poll(250, TimeUnit.MILLISECONDS);
                if (entry == null) continue;
                if (entry == STOP) break;
                BufferedWriter target = entry.event ? events : samples;
                target.write(entry.json);
                target.newLine();
                if (entry.event) eventsWritten.incrementAndGet();
                else samplesWritten.incrementAndGet();
            }
            samples.flush();
            events.flush();
        } catch (Exception exception) {
            if (!(exception instanceof InterruptedException)) {
                writeErrors.incrementAndGet();
                LOGGER.warn("Runtime memory profiling writer disabled: {}",
                        exception.getClass().getSimpleName());
            }
            active.set(false);
            queue.clear();
            Thread.currentThread().interrupt();
        }
    }

    private BufferedWriter writer(Path path) throws IOException {
        return Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private void stageEvent(Stage stage, String eventType, long durationMs, Throwable error) {
        Map<String, Object> record = common("stage_event");
        record.put("eventType", eventType);
        record.put("stage", stage.name);
        record.put("stageExecutionId", stage.executionId);
        record.put("parentStageExecutionId", stage.parentExecutionId);
        record.put("jobId", stage.jobId);
        record.put("repositoryId", stage.repositoryId);
        record.put("revision", stage.revision);
        record.put("unitId", stage.unitId);
        record.put("durationMs", durationMs < 0 ? null : durationMs);
        record.put("status", eventType);
        record.put("errorType", error == null ? null : error.getClass().getSimpleName());
        record.put("attributes", Map.copyOf(stage.attributes));
        enqueue(true, record);
    }

    private Map<String, Object> common(String recordType) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("schemaVersion", SCHEMA_VERSION);
        record.put("recordType", recordType);
        record.put("timestampUtc", Instant.now().toString());
        record.put("monotonicNanos", System.nanoTime());
        record.put("runId", runId);
        record.put("service", service);
        record.put("instanceId", instanceId);
        record.put("pid", pid);
        return record;
    }

    private void enqueue(boolean event, Map<String, Object> record) {
        if (!active.get()) return;
        try {
            String json = objectMapper.writeValueAsString(record);
            if (!queue.offer(new Entry(event, json))) droppedRecords.incrementAndGet();
        } catch (Exception exception) {
            droppedRecords.incrementAndGet();
        }
    }

    @PreDestroy
    @Override
    public void close() {
        if (!active.getAndSet(false)) return;
        if (samplerThread != null) samplerThread.interrupt();
        queue.offer(STOP);
        join(samplerThread);
        join(writerThread);
    }

    private void join(Thread thread) {
        if (thread == null || thread == Thread.currentThread()) return;
        try {
            thread.join(2_000);
        } catch (InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static String safeRunId(String value) {
        String candidate = value == null || value.isBlank()
                ? Instant.now().toString().replaceAll("[^0-9]", "").substring(0, 14)
                : value.trim();
        if (!SAFE_RUN_ID.matcher(candidate).matches()) {
            throw new IllegalArgumentException("Invalid memory profile runId");
        }
        return candidate;
    }

    private static String safeService(String value) {
        String candidate = value == null ? "service" : value.trim();
        String cleaned = candidate.replaceAll("[^A-Za-z0-9._-]", "-");
        if (cleaned.isBlank()) return "service";
        return cleaned.substring(0, Math.min(cleaned.length(), 64));
    }

    private static String safeStage(String value) {
        if (value == null || !value.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new IllegalArgumentException("Invalid memory profile stage");
        }
        return value;
    }

    private static Long nullableNonNegative(long value) {
        return value < 0 ? null : value;
    }

    private static Long linuxRssBytes() {
        Path status = Path.of("/proc/self/status");
        if (!Files.isReadable(status)) return null;
        try {
            for (String line : Files.readAllLines(status, StandardCharsets.UTF_8)) {
                if (line.startsWith("VmRSS:")) {
                    String value = line.substring(6).trim().split("\\s+")[0];
                    return Long.parseLong(value) * 1024;
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private record Entry(boolean event, String json) {
    }

    public static final class Stage implements AutoCloseable {

        private static final Stage NOOP = new Stage();
        private final RuntimeMemoryProfiler profiler;
        private final String name;
        private final String executionId;
        private final String parentExecutionId;
        private final Stage parent;
        private final String jobId;
        private final String repositoryId;
        private final String revision;
        private final String unitId;
        private final long startedNanos;
        private final Map<String, Object> attributes = new LinkedHashMap<>();
        private Throwable failure;
        private boolean closed;

        private Stage() {
            profiler = null;
            name = "";
            executionId = "";
            parentExecutionId = null;
            parent = null;
            jobId = null;
            repositoryId = null;
            revision = null;
            unitId = null;
            startedNanos = 0;
            closed = true;
        }

        private Stage(
                RuntimeMemoryProfiler profiler,
                String name,
                String jobId,
                String repositoryId,
                String revision,
                String unitId,
                Stage parent
        ) {
            this.profiler = profiler;
            this.name = name;
            this.executionId = UUID.randomUUID().toString();
            this.parentExecutionId = parent == null ? null : parent.executionId;
            this.parent = parent;
            this.jobId = jobId;
            this.repositoryId = repositoryId;
            this.revision = revision;
            this.unitId = unitId;
            this.startedNanos = System.nanoTime();
            profiler.stageEvent(this, "STARTED", -1, null);
        }

        static Stage noop() {
            return NOOP;
        }

        public Stage attribute(String key, Object value) {
            if (profiler == null) return this;
            if (attributes.size() >= ATTRIBUTE_LIMIT && !attributes.containsKey(key)) {
                throw new IllegalArgumentException("Memory profile attribute limit exceeded");
            }
            if (key == null || !key.matches("[A-Za-z][A-Za-z0-9_]{0,63}")) {
                throw new IllegalArgumentException("Invalid memory profile attribute key");
            }
            if (!(value == null || value instanceof Number || value instanceof Boolean
                    || value instanceof String)) {
                throw new IllegalArgumentException("Memory profile attributes must be scalar");
            }
            if (value instanceof String text && text.length() > ATTRIBUTE_STRING_LIMIT) {
                throw new IllegalArgumentException("Memory profile attribute is too large");
            }
            attributes.put(key, value);
            return this;
        }

        public void failed(Throwable error) {
            this.failure = error;
        }

        @Override
        public void close() {
            if (closed || profiler == null) return;
            closed = true;
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
            profiler.stageEvent(this, failure == null ? "COMPLETED" : "FAILED",
                    durationMs, failure);
            if (parentExecutionId == null) {
                profiler.currentStage.remove();
            } else {
                profiler.currentStage.set(findParent());
            }
        }

        private Stage findParent() {
            Stage current = profiler.currentStage.get();
            if (current != this) return null;
            return parentExecutionId == null ? null : parent;
        }
    }
}
