package com.devcollab.worker.git;

public record GitDiffProjection(
        String path,
        String oldPath,
        String changeType
) {
}

