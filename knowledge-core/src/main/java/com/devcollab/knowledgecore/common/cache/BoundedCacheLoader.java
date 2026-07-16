package com.devcollab.knowledgecore.common.cache;

import com.github.benmanes.caffeine.cache.Cache;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Component
public class BoundedCacheLoader {

    private final ThreadPoolExecutor executor;
    private final CacheProperties properties;

    public BoundedCacheLoader(CacheProperties properties) {
        this.properties = properties;
        CacheProperties.Local local = properties.local();
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(
                    runnable,
                    "devcollab-cache-loader-" + sequence.incrementAndGet()
            );
            thread.setDaemon(true);
            return thread;
        };
        this.executor = new ThreadPoolExecutor(
                local.loadingThreads(),
                local.loadingThreads(),
                30,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(local.loadingQueueCapacity()),
                factory,
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    public <T> T get(
            String cacheName,
            Cache<String, T> cache,
            String key,
            Supplier<T> source
    ) {
        return cache.get(key, ignored -> load(cacheName, key, source));
    }

    private <T> T load(String cacheName, String key, Supplier<T> source) {
        CompletableFuture<T> future = CompletableFuture.supplyAsync(
                source, executor
        );
        try {
            return future.get(
                    properties.local().loadTimeout().toMillis(),
                    TimeUnit.MILLISECONDS
            );
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new CacheLoadTimeoutException(cacheName, key);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Cache load interrupted", exception);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Cache load failed", exception.getCause());
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
