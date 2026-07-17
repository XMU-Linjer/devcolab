package com.devcollab.worker.objectstorage;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class SnapshotSourceRepository {

    private final JdbcTemplate jdbcTemplate;

    public SnapshotSourceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<SnapshotSource> findByVersionId(UUID versionId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    SELECT d.workspace_id,
                           v.document_id,
                           v.id AS version_id,
                           v.version_no,
                           v.snapshot_payload,
                           v.published_by,
                           v.published_at
                    FROM document_versions v
                    JOIN documents d ON d.id = v.document_id
                    WHERE v.id = ?
                    """,
                    (rs, rowNum) -> new SnapshotSource(
                            rs.getObject("workspace_id", UUID.class),
                            rs.getObject("document_id", UUID.class),
                            rs.getObject("version_id", UUID.class),
                            rs.getInt("version_no"),
                            rs.getString("snapshot_payload"),
                            rs.getObject("published_by", UUID.class),
                            rs.getTimestamp("published_at").toInstant()
                    ),
                    versionId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public record SnapshotSource(
            UUID workspaceId,
            UUID documentId,
            UUID versionId,
            int versionNo,
            String snapshotPayload,
            UUID publishedBy,
            java.time.Instant publishedAt
    ) {
    }
}
