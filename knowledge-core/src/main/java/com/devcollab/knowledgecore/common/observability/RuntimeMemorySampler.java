package com.devcollab.knowledgecore.common.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
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

/**
 * Opt-in, non-blocking process sampler for Knowledge Core. Business stages live in
 * the worker that owns the background repository job.
 */
@Component
public class RuntimeMemorySampler implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeMemorySampler.class);
    private static final Pattern SAFE_RUN_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final String STOP = "__STOP__";

    private final ObjectMapper objectMapper;
    private final ArrayBlockingQueue<String> queue;
    private final AtomicBoolean active = new AtomicBoolean();
    private final AtomicLong samplesWritten = new AtomicLong();
    private final AtomicLong droppedRecords = new AtomicLong();
    private final AtomicLong writeErrors = new AtomicLong();
    private final long intervalMs;
    private final long pid = ProcessHandle.current().pid();
    private final String runId;
    private final String instanceId;
    private final Path outputFile;
    private Thread sampler;
    private Thread writer;

    public RuntimeMemorySampler(
            ObjectMapper objectMapper,
            @Value("${devcollab.memory-profile.enabled:false}") boolean enabled,
            @Value("${devcollab.memory-profile.run-id:}") String configuredRunId,
            @Value("${devcollab.memory-profile.output-dir:.data/memory-profile}") String outputDir,
            @Value("${devcollab.memory-profile.interval-ms:1000}") long configuredIntervalMs,
            @Value("${devcollab.memory-profile.queue-capacity:1024}") int capacity
    ) {
        this.objectMapper = objectMapper;
        this.intervalMs = configuredIntervalMs >= 500 ? configuredIntervalMs : 1000;
        this.queue = new ArrayBlockingQueue<>(Math.max(1, Math.min(capacity, 65_536)));
        this.runId = enabled ? safeRunId(configuredRunId) : "disabled";
        this.instanceId = "knowledge-core-" + pid + "-" + UUID.randomUUID()
                .toString().substring(0, 8);
        Path root = Path.of(outputDir).toAbsolutePath().normalize();
        Path runDirectory = root.resolve(runId).normalize();
        if (!runDirectory.startsWith(root)) {
            throw new IllegalArgumentException("Memory profile run directory escapes output root");
        }
        this.outputFile = runDirectory.resolve("knowledge-core-" + pid + "-samples.jsonl");
        if (enabled) start(runDirectory);
    }

    private void start(Path runDirectory) {
        try {
            Files.createDirectories(runDirectory);
            active.set(true);
            writer = new Thread(this::writeLoop, "knowledge-core-memory-profile-writer");
            sampler = new Thread(this::sampleLoop, "knowledge-core-memory-profile-sampler");
            writer.setDaemon(true);
            sampler.setDaemon(true);
            writer.start();
            sampler.start();
        } catch (Exception exception) {
            active.set(false);
            LOGGER.warn("Runtime memory profiling disabled because output initialization failed: {}",
                    exception.getClass().getSimpleName());
        }
    }

    private void sampleLoop() {
        while (active.get()) {
            try {
                if (!queue.offer(objectMapper.writeValueAsString(sample()))) {
                    droppedRecords.incrementAndGet();
                }
                Thread.sleep(intervalMs);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception exception) {
                active.set(false);
                LOGGER.warn("Runtime memory sampling disabled: {}",
                        exception.getClass().getSimpleName());
            }
        }
    }

    private Map<String, Object> sample() {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        MemoryUsage nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        BufferPoolMXBean direct = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)
                .stream().filter(pool -> "direct".equals(pool.getName())).findFirst().orElse(null);
        var osBean = ManagementFactory.getOperatingSystemMXBean();
        Long virtualMemory = null;
        Double cpuPercent = null;
        Long openFiles = null;
        if (osBean instanceof com.sun.management.OperatingSystemMXBean os) {
            virtualMemory = nullable(os.getCommittedVirtualMemorySize());
            double processCpuLoad = os.getProcessCpuLoad();
            cpuPercent = processCpuLoad < 0 ? null : processCpuLoad * 100;
        }
        if (osBean instanceof com.sun.management.UnixOperatingSystemMXBean unix) {
            openFiles = nullable(unix.getOpenFileDescriptorCount());
        }
        Long metaspace = ManagementFactory.getMemoryPoolMXBeans().stream()
                .filter(pool -> "Metaspace".equals(pool.getName()))
                .map(MemoryPoolMXBean::getUsage).filter(usage -> usage != null)
                .map(MemoryUsage::getUsed).findFirst().orElse(null);
        long gcCount = ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionCount)
                .filter(value -> value >= 0).sum();
        long gcTime = ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionTime)
                .filter(value -> value >= 0).sum();
        var threads = ManagementFactory.getThreadMXBean();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", 1);
        result.put("recordType", "sample");
        result.put("timestampUtc", Instant.now().toString());
        result.put("monotonicNanos", System.nanoTime());
        result.put("runId", runId);
        result.put("service", "knowledge-core");
        result.put("instanceId", instanceId);
        result.put("pid", pid);
        result.put("rssBytes", linuxRssBytes());
        result.put("virtualMemoryBytes", virtualMemory);
        result.put("cpuPercent", cpuPercent);
        result.put("threadCount", threads.getThreadCount());
        result.put("peakThreadCount", threads.getPeakThreadCount());
        result.put("openFileDescriptorCount", openFiles);
        result.put("heapUsedBytes", heap.getUsed());
        result.put("heapCommittedBytes", heap.getCommitted());
        result.put("heapMaxBytes", heap.getMax());
        result.put("nonHeapUsedBytes", nonHeap.getUsed());
        result.put("metaspaceUsedBytes", metaspace);
        result.put("directBufferUsedBytes", direct == null ? null : direct.getMemoryUsed());
        result.put("directBufferCapacityBytes",
                direct == null ? null : direct.getTotalCapacity());
        result.put("directBufferCount", direct == null ? null : direct.getCount());
        result.put("gcCount", gcCount);
        result.put("gcTimeMs", gcTime);
        result.put("pythonAllocatedBytes", null);
        result.put("pythonPeakAllocatedBytes", null);
        result.put("processUptimeMs", ManagementFactory.getRuntimeMXBean().getUptime());
        result.put("samplesWritten", samplesWritten.get());
        result.put("eventsWritten", 0);
        result.put("droppedRecords", droppedRecords.get());
        result.put("writeErrors", writeErrors.get());
        result.put("profilingOverheadWarning", droppedRecords.get() > 0);
        return result;
    }

    private void writeLoop() {
        try (BufferedWriter output = Files.newBufferedWriter(
                outputFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND
        )) {
            while (active.get() || !queue.isEmpty()) {
                String json = queue.poll(250, TimeUnit.MILLISECONDS);
                if (json == null) continue;
                if (STOP.equals(json)) break;
                output.write(json);
                output.newLine();
                samplesWritten.incrementAndGet();
            }
            output.flush();
        } catch (Exception exception) {
            if (!(exception instanceof InterruptedException)) {
                writeErrors.incrementAndGet();
                LOGGER.warn("Runtime memory profiling writer disabled: {}",
                        exception.getClass().getSimpleName());
            }
            active.set(false);
            queue.clear();
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @PreDestroy
    @Override
    public void close() {
        if (!active.getAndSet(false)) return;
        if (sampler != null) sampler.interrupt();
        queue.offer(STOP);
        join(sampler);
        join(writer);
    }

    private void join(Thread thread) {
        if (thread == null || thread == Thread.currentThread()) return;
        try {
            thread.join(2_000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
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

    private static Long nullable(long value) {
        return value < 0 ? null : value;
    }

    private static Long linuxRssBytes() {
        Path status = Path.of("/proc/self/status");
        if (!Files.isReadable(status)) return null;
        try {
            for (String line : Files.readAllLines(status, StandardCharsets.UTF_8)) {
                if (line.startsWith("VmRSS:")) {
                    return Long.parseLong(line.substring(6).trim().split("\\s+")[0]) * 1024;
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }
}
