package com.devcollab.worker.git;

public record GitRepositoryFileProjection(
        String path,
        String blobSha,
        long sizeBytes,
        String language,
        String contentText
) {
    public GitRepositoryFileProjection(
            String path,
            String blobSha,
            long sizeBytes,
            String language
    ) {
        this(path, blobSha, sizeBytes, language, null);
    }
}
