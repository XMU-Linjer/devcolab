package com.devcollab.knowledgecore.git.api;

import com.devcollab.knowledgecore.git.application.GitChangeDetails;
import com.devcollab.knowledgecore.git.domain.GitChangeType;
import com.devcollab.knowledgecore.git.domain.GitFileChangeType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GitChangeResponse(
        UUID id,
        UUID repositoryId,
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
        boolean duplicate,
        List<FileDiffResponse> files
) {
    public static GitChangeResponse from(GitChangeDetails details) {
        var change = details.change();
        return new GitChangeResponse(
                change.id(), change.repositoryId(), change.changeType(),
                change.externalId(), change.title(), change.commitSha(),
                change.baseRef(), change.headRef(), change.authorName(),
                change.authorEmail(), change.authoredAt(),
                change.committerName(), change.committerEmail(),
                change.parentCommitSha(),
                change.webUrl(), change.occurredAt(), details.duplicate(),
                details.files().stream().map(FileDiffResponse::from).toList()
        );
    }

    public record FileDiffResponse(
            UUID id,
            String path,
            String oldPath,
            GitFileChangeType changeType,
            int additions,
            int deletions,
            boolean binaryFile,
            String patchExcerpt
    ) {
        static FileDiffResponse from(
                com.devcollab.knowledgecore.git.domain.GitFileDiff diff
        ) {
            return new FileDiffResponse(
                    diff.id(), diff.path(), diff.oldPath(), diff.changeType(),
                    diff.additions(), diff.deletions(), diff.binaryFile(),
                    diff.patchExcerpt()
            );
        }
    }
}
