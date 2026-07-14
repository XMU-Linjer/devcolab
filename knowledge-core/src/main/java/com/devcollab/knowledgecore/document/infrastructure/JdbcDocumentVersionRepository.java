package com.devcollab.knowledgecore.document.infrastructure;

import com.devcollab.knowledgecore.document.domain.DocumentVersion;
import com.devcollab.knowledgecore.document.domain.DocumentVersionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcDocumentVersionRepository implements DocumentVersionRepository {

    private static final RowMapper<DocumentVersion> DOCUMENT_VERSION_ROW_MAPPER =
            (rs, rowNum) -> new DocumentVersion(
                    rs.getObject("id", UUID.class),
                    rs.getObject("document_id", UUID.class),
                    rs.getInt("version_no"),
                    rs.getString("title"),
                    rs.getString("snapshot_payload"),
                    rs.getObject("published_by", UUID.class),
                    rs.getTimestamp("published_at").toInstant()
            );

    private final JdbcTemplate jdbcTemplate;

    public JdbcDocumentVersionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public DocumentVersion save(DocumentVersion version) {
        jdbcTemplate.update("""
                        INSERT INTO document_versions
                            (id, document_id, version_no, title,
                             snapshot_payload, published_by, published_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                version.id(),
                version.documentId(),
                version.versionNo(),
                version.title(),
                version.snapshotPayload(),
                version.publishedBy(),
                Timestamp.from(version.publishedAt()));
        return version;
    }

    @Override
    public int nextVersionNo(UUID documentId) {
        Integer currentMax = jdbcTemplate.queryForObject("""
                        SELECT COALESCE(MAX(version_no), 0)
                          FROM document_versions
                         WHERE document_id = ?
                        """,
                Integer.class,
                documentId);
        return (currentMax == null ? 0 : currentMax) + 1;
    }

    @Override
    public Optional<DocumentVersion> findById(UUID versionId) {
        return jdbcTemplate.query("""
                        SELECT * FROM document_versions WHERE id = ?
                        """,
                DOCUMENT_VERSION_ROW_MAPPER,
                versionId
        ).stream().findFirst();
    }

    @Override
    public List<DocumentVersion> findAllByDocumentId(UUID documentId) {
        return jdbcTemplate.query("""
                        SELECT * FROM document_versions
                         WHERE document_id = ?
                         ORDER BY version_no DESC
                        """,
                DOCUMENT_VERSION_ROW_MAPPER,
                documentId
        );
    }
}
