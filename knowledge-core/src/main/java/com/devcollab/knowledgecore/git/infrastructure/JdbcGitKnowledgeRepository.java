package com.devcollab.knowledgecore.git.infrastructure;

import com.devcollab.knowledgecore.git.domain.CodeDocumentBinding;
import com.devcollab.knowledgecore.git.domain.GitChange;
import com.devcollab.knowledgecore.git.domain.GitChangeType;
import com.devcollab.knowledgecore.git.domain.GitFileChangeType;
import com.devcollab.knowledgecore.git.domain.GitFileDiff;
import com.devcollab.knowledgecore.git.domain.GitKnowledgeRepository;
import com.devcollab.knowledgecore.git.domain.GitProvider;
import com.devcollab.knowledgecore.git.domain.GitRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcGitKnowledgeRepository implements GitKnowledgeRepository {

    private static final RowMapper<GitRepository> REPOSITORY_MAPPER =
            (rs, rowNum) -> new GitRepository(
                    rs.getObject("id", UUID.class),
                    rs.getObject("workspace_id", UUID.class),
                    rs.getString("name"),
                    GitProvider.valueOf(rs.getString("provider")),
                    rs.getString("remote_url"),
                    rs.getString("default_branch"),
                    rs.getObject("created_by", UUID.class),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()
            );

    private static final RowMapper<GitChange> CHANGE_MAPPER =
            (rs, rowNum) -> new GitChange(
                    rs.getObject("id", UUID.class),
                    rs.getObject("repository_id", UUID.class),
                    GitChangeType.valueOf(rs.getString("change_type")),
                    rs.getString("external_id"),
                    rs.getString("title"),
                    rs.getString("commit_sha"),
                    rs.getString("base_ref"),
                    rs.getString("head_ref"),
                    rs.getString("author_name"),
                    rs.getString("web_url"),
                    rs.getTimestamp("occurred_at").toInstant(),
                    rs.getTimestamp("created_at").toInstant()
            );

    private static final RowMapper<GitFileDiff> DIFF_MAPPER =
            (rs, rowNum) -> new GitFileDiff(
                    rs.getObject("id", UUID.class),
                    rs.getObject("git_change_id", UUID.class),
                    rs.getString("path"),
                    rs.getString("old_path"),
                    GitFileChangeType.valueOf(rs.getString("change_type")),
                    rs.getInt("additions"),
                    rs.getInt("deletions"),
                    rs.getString("patch_excerpt")
            );

    private static final RowMapper<CodeDocumentBinding> BINDING_MAPPER =
            (rs, rowNum) -> new CodeDocumentBinding(
                    rs.getObject("id", UUID.class),
                    rs.getObject("workspace_id", UUID.class),
                    rs.getObject("repository_id", UUID.class),
                    rs.getObject("document_id", UUID.class),
                    rs.getObject("block_id", UUID.class),
                    rs.getString("path_pattern"),
                    rs.getObject("created_by", UUID.class),
                    rs.getTimestamp("created_at").toInstant()
            );

    private final JdbcTemplate jdbcTemplate;

    public JdbcGitKnowledgeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public GitRepository saveRepository(GitRepository repository) {
        jdbcTemplate.update("""
                        INSERT INTO git_repositories
                            (id, workspace_id, name, provider, remote_url,
                             default_branch, created_by, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                repository.id(), repository.workspaceId(), repository.name(),
                repository.provider().name(), repository.remoteUrl(),
                repository.defaultBranch(), repository.createdBy(),
                Timestamp.from(repository.createdAt()),
                Timestamp.from(repository.updatedAt()));
        return repository;
    }

    @Override
    public Optional<GitRepository> findRepositoryById(UUID repositoryId) {
        return jdbcTemplate.query(
                "SELECT * FROM git_repositories WHERE id = ?",
                REPOSITORY_MAPPER,
                repositoryId
        ).stream().findFirst();
    }

    @Override
    public Optional<GitRepository> findRepositoryByRemoteUrl(
            UUID workspaceId,
            String remoteUrl
    ) {
        return jdbcTemplate.query("""
                        SELECT * FROM git_repositories
                         WHERE workspace_id = ? AND remote_url = ?
                        """, REPOSITORY_MAPPER, workspaceId, remoteUrl)
                .stream().findFirst();
    }

    @Override
    public List<GitRepository> findRepositoriesByWorkspaceId(UUID workspaceId) {
        return jdbcTemplate.query("""
                        SELECT * FROM git_repositories
                         WHERE workspace_id = ? ORDER BY created_at DESC
                        """, REPOSITORY_MAPPER, workspaceId);
    }

    @Override
    public GitChange saveChange(GitChange change) {
        jdbcTemplate.update("""
                        INSERT INTO git_changes
                            (id, repository_id, change_type, external_id, title,
                             commit_sha, base_ref, head_ref, author_name, web_url,
                             occurred_at, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                change.id(), change.repositoryId(), change.changeType().name(),
                change.externalId(), change.title(), change.commitSha(),
                change.baseRef(), change.headRef(), change.authorName(),
                change.webUrl(), Timestamp.from(change.occurredAt()),
                Timestamp.from(change.createdAt()));
        return change;
    }

    @Override
    public Optional<GitChange> findChangeById(UUID changeId) {
        return jdbcTemplate.query(
                "SELECT * FROM git_changes WHERE id = ?",
                CHANGE_MAPPER,
                changeId
        ).stream().findFirst();
    }

    @Override
    public Optional<GitChange> findChangeByExternalIdentity(
            UUID repositoryId,
            GitChangeType type,
            String externalId
    ) {
        return jdbcTemplate.query("""
                        SELECT * FROM git_changes
                         WHERE repository_id = ? AND change_type = ?
                           AND external_id = ?
                        """, CHANGE_MAPPER, repositoryId, type.name(), externalId)
                .stream().findFirst();
    }

    @Override
    public List<GitChange> findChangesByRepositoryId(UUID repositoryId) {
        return jdbcTemplate.query("""
                        SELECT * FROM git_changes
                         WHERE repository_id = ?
                         ORDER BY occurred_at DESC, created_at DESC
                        """, CHANGE_MAPPER, repositoryId);
    }

    @Override
    public void saveDiffs(List<GitFileDiff> diffs) {
        for (GitFileDiff diff : diffs) {
            jdbcTemplate.update("""
                            INSERT INTO git_file_diffs
                                (id, git_change_id, path, old_path, change_type,
                                 additions, deletions, patch_excerpt)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    diff.id(), diff.gitChangeId(), diff.path(), diff.oldPath(),
                    diff.changeType().name(), diff.additions(), diff.deletions(),
                    diff.patchExcerpt());
        }
    }

    @Override
    public List<GitFileDiff> findDiffsByChangeId(UUID changeId) {
        return jdbcTemplate.query("""
                        SELECT * FROM git_file_diffs
                         WHERE git_change_id = ? ORDER BY path
                        """, DIFF_MAPPER, changeId);
    }

    @Override
    public CodeDocumentBinding saveBinding(CodeDocumentBinding binding) {
        jdbcTemplate.update("""
                        INSERT INTO code_document_bindings
                            (id, workspace_id, repository_id, document_id,
                             block_id, target_key, path_pattern, created_by, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                binding.id(), binding.workspaceId(), binding.repositoryId(),
                binding.documentId(), binding.blockId(),
                binding.blockId() == null ? "DOCUMENT" : binding.blockId().toString(),
                binding.pathPattern(),
                binding.createdBy(), Timestamp.from(binding.createdAt()));
        return binding;
    }

    @Override
    public Optional<CodeDocumentBinding> findBindingById(UUID bindingId) {
        return jdbcTemplate.query(
                "SELECT * FROM code_document_bindings WHERE id = ?",
                BINDING_MAPPER,
                bindingId
        ).stream().findFirst();
    }

    @Override
    public List<CodeDocumentBinding> findBindingsByDocumentId(UUID documentId) {
        return jdbcTemplate.query("""
                        SELECT * FROM code_document_bindings
                         WHERE document_id = ? ORDER BY created_at DESC
                        """, BINDING_MAPPER, documentId);
    }

    @Override
    public List<CodeDocumentBinding> findBindingsByRepositoryId(UUID repositoryId) {
        return jdbcTemplate.query("""
                        SELECT * FROM code_document_bindings
                         WHERE repository_id = ? ORDER BY created_at DESC
                        """, BINDING_MAPPER, repositoryId);
    }

    @Override
    public void deleteBinding(UUID bindingId) {
        jdbcTemplate.update(
                "DELETE FROM code_document_bindings WHERE id = ?",
                bindingId
        );
    }
}
