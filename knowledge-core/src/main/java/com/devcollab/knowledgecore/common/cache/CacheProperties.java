package com.devcollab.knowledgecore.common.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "devcollab.cache")
public record CacheProperties(
        Duration workspaceMemberTtl,
        Duration documentTreeTtl
) {

    public CacheProperties {
        if (workspaceMemberTtl == null) {
            workspaceMemberTtl = Duration.ofMinutes(10);
        }
        if (documentTreeTtl == null) {
            documentTreeTtl = Duration.ofMinutes(5);
        }
    }
}