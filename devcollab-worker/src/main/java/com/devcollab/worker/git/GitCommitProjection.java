package com.devcollab.worker.git;

import java.time.Instant;
import java.util.List;

public record GitCommitProjection(
        String commitSha,
        String title,
        String authorName,
        String authorEmail,
        Instant authoredAt,
        String committerName,
        String committerEmail,
        Instant committedAt,
        String parentCommitSha,
        List<GitDiffProjection> files
) {
}
