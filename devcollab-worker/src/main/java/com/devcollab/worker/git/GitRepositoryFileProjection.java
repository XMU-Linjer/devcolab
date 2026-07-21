package com.devcollab.worker.git;

public record GitRepositoryFileProjection(
        String path,
        String blobSha,
        long sizeBytes,
        String language
) {
}

