package com.devcollab.knowledgecore.common.outbox.worker;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "devcollab.worker.outbox")
public record OutboxWorkerProperties(
        boolean enabled,
        int batchSize,
        int maxRetryCount
) {
    public OutboxWorkerProperties {
        if (batchSize <= 0) {
            batchSize = 50;
        }
        if (maxRetryCount <= 0) {
            maxRetryCount = 3;
        }
    }
}
