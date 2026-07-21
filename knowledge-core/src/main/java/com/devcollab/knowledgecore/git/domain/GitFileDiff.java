package com.devcollab.knowledgecore.git.domain;

import java.util.UUID;

public record GitFileDiff(
        UUID id,
        UUID gitChangeId,
        String path,
        String oldPath,
        GitFileChangeType changeType,
        int additions,
        int deletions,
        boolean binaryFile,
        String patchExcerpt
) {
}
