package com.devcollab.worker.objectstorage;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
public class StoredObjectMetadataRepository {

    private final JdbcTemplate jdbcTemplate;

    public StoredObjectMetadataRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean existsBySourceEventId(UUID sourceEventId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM stored_objects
                WHERE source_event_id = ?
                """,
                Integer.class,
                sourceEventId
        );
        return count != null && count > 0;
    }

    public boolean save(StoredObjectMetadata metadata) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO stored_objects (
                        id, workspace_id, owner_user_id, object_type,
                        bucket, object_key, content_type, size_bytes,
                        checksum_sha256, reference_type, reference_id,
                        source_event_id, status, created_at, deleted_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)
                    """,
                    metadata.id(),
                    metadata.workspaceId(),
                    metadata.ownerUserId(),
                    metadata.objectType(),
                    metadata.bucket(),
                    metadata.objectKey(),
                    metadata.contentType(),
                    metadata.sizeBytes(),
                    metadata.checksumSha256(),
                    metadata.referenceType(),
                    metadata.referenceId(),
                    metadata.sourceEventId(),
                    metadata.status(),
                    Timestamp.from(metadata.createdAt())
            );
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    public record StoredObjectMetadata(
            UUID id,
            UUID workspaceId,
            UUID ownerUserId,
            String objectType,
            String bucket,
            String objectKey,
            String contentType,
            long sizeBytes,
            String checksumSha256,
            String referenceType,
            UUID referenceId,
            UUID sourceEventId,
            String status,
            Instant createdAt
    ) {
    }
}
