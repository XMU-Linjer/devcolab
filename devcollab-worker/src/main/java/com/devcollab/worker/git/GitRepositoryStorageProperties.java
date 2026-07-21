package com.devcollab.worker.git;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.List;

@ConfigurationProperties(prefix = "devcollab.worker.git")
public record GitRepositoryStorageProperties(
        boolean enabled,
        String groupId,
        Path dataRoot,
        List<String> allowedHosts,
        int timeoutSeconds,
        int maxFiles,
        int maxCommits,
        long maxRepositoryBytes
) {
}

