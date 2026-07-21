package com.devcollab.worker.git;

import java.time.Instant;
import java.util.List;

public record GitCommitProjection(
        String commitSha,
        String title,
        String authorName,
        Instant occurredAt,
        List<GitDiffProjection> files
) {
}

