package com.devcollab.worker.git;

public record GitDiffProjection(
        String path,
        String oldPath,
        String changeType,
        int additions,
        int deletions,
        boolean binaryFile,
        String patchExcerpt
) {
}
