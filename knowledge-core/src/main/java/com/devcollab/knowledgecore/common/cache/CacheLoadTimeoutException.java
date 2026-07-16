package com.devcollab.knowledgecore.common.cache;

public class CacheLoadTimeoutException extends RuntimeException {
    public CacheLoadTimeoutException(String cacheName, String key) {
        super("Cache load timed out: cache=" + cacheName + ", key=" + key);
    }
}
