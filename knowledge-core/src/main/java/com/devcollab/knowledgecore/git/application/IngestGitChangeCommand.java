package com.devcollab.knowledgecore.git.application;

import com.devcollab.knowledgecore.git.domain.GitChangeType;
import com.devcollab.knowledgecore.git.domain.GitFileChangeType;

import java.time.Instant;
import java.util.List;

public record IngestGitChangeCommand(
        GitChangeType changeType,
        String externalId,
        String title,
        String commitSha,
        String baseRef,
        String headRef,
        String authorName,
        String authorEmail,
        Instant authoredAt,
        String committerName,
        String committerEmail,
        String parentCommitSha,
        String webUrl,
        Instant occurredAt,
        List<FileDiff> files
) {
    public record FileDiff(
            String path,
            String oldPath,
            GitFileChangeType changeType,
            int additions,
            int deletions,
            boolean binaryFile,
            String patchExcerpt
    ) {
    }
}
