package com.devcollab.worker.git;

public record CodeFileDependencyProjection(
        String sourcePath,
        String targetPath,
        String relationType
) {
}
