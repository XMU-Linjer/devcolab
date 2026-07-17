package com.devcollab.worker.objectstorage;

import com.devcollab.worker.objectstorage.SnapshotSourceRepository.SnapshotSource;
import com.devcollab.worker.objectstorage.StoredObjectMetadataRepository.StoredObjectMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
@ConditionalOnProperty(
        name = {
                "devcollab.worker.snapshot.enabled",
                "devcollab.object-storage.minio.enabled"
        },
        havingValue = "true"
)
public class SnapshotProjectionService {

    private final SnapshotSourceRepository sourceRepository;
    private final StoredObjectMetadataRepository metadataRepository;
    private final ObjectStorageGateway objectStorageGateway;
    private final MinioObjectStorageProperties properties;
    private final ObjectMapper objectMapper;

    public SnapshotProjectionService(
            SnapshotSourceRepository sourceRepository,
            StoredObjectMetadataRepository metadataRepository,
            ObjectStorageGateway objectStorageGateway,
            MinioObjectStorageProperties properties,
            ObjectMapper objectMapper
    ) {
        this.sourceRepository = sourceRepository;
        this.metadataRepository = metadataRepository;
        this.objectStorageGateway = objectStorageGateway;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public ProjectionResult project(UUID eventId, String payload) {
        if (metadataRepository.existsBySourceEventId(eventId)) {
            return ProjectionResult.ALREADY_PROJECTED;
        }

        SnapshotRequest request = parseRequest(payload);
        SnapshotSource source = sourceRepository.findByVersionId(
                request.versionId()
        ).orElseThrow(() -> new IllegalStateException(
                "Snapshot source version not found: " + request.versionId()
        ));
        validateRequest(request, source);

        byte[] content = source.snapshotPayload()
                .getBytes(StandardCharsets.UTF_8);
        String checksum = sha256(content);
        String objectKey = objectKey(source);
        ObjectStorageGateway.StoredObjectWriteResult stored =
                objectStorageGateway.put(
                        properties.snapshotBucket(),
                        objectKey,
                        content,
                        "application/json",
                        Map.of(
                                "sha256", checksum,
                                "document-id", source.documentId().toString(),
                                "version-id", source.versionId().toString()
                        )
                );

        boolean inserted = metadataRepository.save(new StoredObjectMetadata(
                UUID.randomUUID(),
                source.workspaceId(),
                source.publishedBy(),
                "DOCUMENT_SNAPSHOT",
                stored.bucket(),
                stored.objectKey(),
                "application/json",
                stored.sizeBytes(),
                checksum,
                "DOCUMENT_VERSION",
                source.versionId(),
                eventId,
                "AVAILABLE",
                Instant.now()
        ));
        return inserted
                ? ProjectionResult.CREATED
                : ProjectionResult.ALREADY_PROJECTED;
    }

    private SnapshotRequest parseRequest(String payload) {
        try {
            JsonNode json = objectMapper.readTree(payload);
            return new SnapshotRequest(
                    uuid(json, "workspaceId"),
                    uuid(json, "documentId"),
                    uuid(json, "versionId"),
                    json.path("versionNo").asInt(-1)
            );
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Snapshot request payload cannot be parsed", exception
            );
        }
    }

    private UUID uuid(JsonNode json, String field) {
        String value = json.path(field).asText();
        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    "Snapshot request missing field: " + field
            );
        }
        return UUID.fromString(value);
    }

    private void validateRequest(
            SnapshotRequest request,
            SnapshotSource source
    ) {
        if (!request.workspaceId().equals(source.workspaceId())
                || !request.documentId().equals(source.documentId())
                || !request.versionId().equals(source.versionId())
                || request.versionNo() != source.versionNo()) {
            throw new IllegalArgumentException(
                    "Snapshot request does not match authoritative version data"
            );
        }
    }

    private String objectKey(SnapshotSource source) {
        return "workspaces/%s/documents/%s/versions/v%d/snapshot.json"
                .formatted(
                        source.workspaceId(),
                        source.documentId(),
                        source.versionNo()
                );
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public enum ProjectionResult {
        CREATED,
        ALREADY_PROJECTED
    }

    private record SnapshotRequest(
            UUID workspaceId,
            UUID documentId,
            UUID versionId,
            int versionNo
    ) {
    }
}
