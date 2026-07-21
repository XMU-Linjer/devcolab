package com.devcollab.worker.git;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class GitRepositoryProjectionStore {

    private final JdbcTemplate jdbcTemplate;

    public GitRepositoryProjectionStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean markSyncing(UUID repositoryId) {
        return jdbcTemplate.update("""
                UPDATE git_repositories
                   SET sync_status = 'SYNCING', last_sync_error = NULL,
                       updated_at = ?
                 WHERE id = ?
                """, Timestamp.from(Instant.now()), repositoryId) == 1;
    }

    @Transactional
    public void replaceFiles(
            UUID repositoryId,
            List<GitRepositoryFileProjection> files
    ) {
        jdbcTemplate.update(
                "DELETE FROM git_repository_files WHERE repository_id = ?",
                repositoryId
        );
        for (GitRepositoryFileProjection file : files) {
            jdbcTemplate.update("""
                    INSERT INTO git_repository_files
                        (id, repository_id, path, blob_sha, size_bytes, language)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), repositoryId, file.path(),
                    file.blobSha(), file.sizeBytes(), file.language());
        }
    }

    @Transactional
    public void saveCommits(UUID repositoryId, List<GitCommitProjection> commits) {
        for (GitCommitProjection commit : commits) {
            List<UUID> existingIds = jdbcTemplate.query("""
                    SELECT id FROM git_changes
                     WHERE repository_id = ? AND change_type = 'COMMIT'
                       AND external_id = ?
                    """, (rs, rowNum) -> rs.getObject("id", UUID.class),
                    repositoryId, commit.commitSha());
            UUID changeId;
            if (existingIds.isEmpty()) {
                changeId = UUID.randomUUID();
                jdbcTemplate.update("""
                        INSERT INTO git_changes
                            (id, repository_id, change_type, external_id, title,
                             commit_sha, author_name, author_email, authored_at,
                             committer_name, committer_email, parent_commit_sha,
                             occurred_at, created_at)
                        VALUES (?, ?, 'COMMIT', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, changeId, repositoryId, commit.commitSha(),
                        commit.title(), commit.commitSha(), commit.authorName(),
                        commit.authorEmail(), timestamp(commit.authoredAt()),
                        commit.committerName(), commit.committerEmail(),
                        commit.parentCommitSha(), Timestamp.from(commit.committedAt()),
                        Timestamp.from(Instant.now()));
            } else {
                changeId = existingIds.getFirst();
                jdbcTemplate.update("""
                        UPDATE git_changes
                           SET title = ?, commit_sha = ?, author_name = ?,
                               author_email = ?, authored_at = ?,
                               committer_name = ?, committer_email = ?,
                               parent_commit_sha = ?, occurred_at = ?
                         WHERE id = ?
                        """, commit.title(), commit.commitSha(), commit.authorName(),
                        commit.authorEmail(), timestamp(commit.authoredAt()),
                        commit.committerName(), commit.committerEmail(),
                        commit.parentCommitSha(), Timestamp.from(commit.committedAt()),
                        changeId);
                jdbcTemplate.update(
                        "DELETE FROM git_file_diffs WHERE git_change_id = ?",
                        changeId
                );
            }
            for (GitDiffProjection file : commit.files()) {
                jdbcTemplate.update("""
                        INSERT INTO git_file_diffs
                            (id, git_change_id, path, old_path, change_type,
                             additions, deletions, binary_file, patch_excerpt)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, UUID.randomUUID(), changeId, file.path(),
                        file.oldPath(), file.changeType(), file.additions(),
                        file.deletions(), file.binaryFile(), file.patchExcerpt());
            }
        }
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    public void markReady(UUID repositoryId, String headCommit) {
        jdbcTemplate.update("""
                UPDATE git_repositories
                   SET sync_status = 'READY', last_synced_commit = ?,
                       last_synced_at = ?, last_sync_error = NULL,
                       updated_at = ?
                 WHERE id = ?
                """, headCommit, Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()), repositoryId);
    }

    public void markFailed(UUID repositoryId, String error) {
        String safeError = error == null ? "未知同步错误"
                : error.substring(0, Math.min(error.length(), 1000));
        jdbcTemplate.update("""
                UPDATE git_repositories
                   SET sync_status = 'FAILED', last_sync_error = ?,
                       updated_at = ?
                 WHERE id = ?
                """, safeError, Timestamp.from(Instant.now()), repositoryId);
    }
}
