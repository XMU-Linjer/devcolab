package com.devcollab.knowledgecore.git.api;

import com.devcollab.knowledgecore.git.domain.GitChangeType;
import com.devcollab.knowledgecore.git.domain.GitFileChangeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record IngestGitChangeRequest(
        @NotNull GitChangeType changeType,
        @NotBlank @Size(max = 200) String externalId,
        @NotBlank @Size(max = 500) String title,
        @NotBlank @Size(max = 64) String commitSha,
        @Size(max = 200) String baseRef,
        @Size(max = 200) String headRef,
        @Size(max = 200) String authorName,
        @Size(max = 320) String authorEmail,
        Instant authoredAt,
        @Size(max = 200) String committerName,
        @Size(max = 320) String committerEmail,
        @Size(max = 64) String parentCommitSha,
        @Size(max = 1000) String webUrl,
        @NotNull Instant occurredAt,
        @NotEmpty @Size(max = 500) List<@Valid FileDiffRequest> files
) {
    public record FileDiffRequest(
            @NotBlank @Size(max = 1000) String path,
            @Size(max = 1000) String oldPath,
            @NotNull GitFileChangeType changeType,
            @Min(0) int additions,
            @Min(0) int deletions,
            boolean binaryFile,
            @Size(max = 8000) String patchExcerpt
    ) {
    }
}
