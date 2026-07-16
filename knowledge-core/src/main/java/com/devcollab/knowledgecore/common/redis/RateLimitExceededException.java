package com.devcollab.knowledgecore.common.redis;

public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(String message, long retryAfterMillis) {
        super(message);
        this.retryAfterSeconds = Math.max(1, (retryAfterMillis + 999) / 1000);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
