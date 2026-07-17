package com.devcollab.worker.objectstorage;

import com.devcollab.worker.objectstorage.SnapshotSourceRepository.SnapshotSource;
import com.devcollab.worker.objectstorage.StoredObjectMetadataRepository.StoredObjectMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SnapshotProjectionServiceTests {

    private final SnapshotSourceRepository sourceRepository =
            mock(SnapshotSourceRepository.class);
    private final StoredObjectMetadataRepository metadataRepository =
            mock(StoredObjectMetadataRepository.class);
    private final ObjectStorageGateway objectStorageGateway =
            mock(ObjectStorageGateway.class);
    private final MinioObjectStorageProperties properties =
            new MinioObjectStorageProperties(
                    true,
                    "http://localhost:9000",
                    "devcollab",
                    "secret",
                    "devcollab-snapshots"
            );
    private final SnapshotProjectionService service =
            new SnapshotProjectionService(
                    sourceRepository,
                    metadataRepository,
                    objectStorageGateway,
                    properties,
                    new ObjectMapper()
            );

    @Test
    void storesAuthoritativeSnapshotWithDeterministicKeyAndMetadata() throws Exception {
        UUID eventId = UUID.randomUUID();
        SnapshotSource source = source();
        byte[] content = source.snapshotPayload()
                .getBytes(StandardCharsets.UTF_8);
        String expectedKey = "workspaces/%s/documents/%s/versions/v2/snapshot.json"
                .formatted(source.workspaceId(), source.documentId());

        when(metadataRepository.existsBySourceEventId(eventId))
                .thenReturn(false);
        when(sourceRepository.findByVersionId(source.versionId()))
                .thenReturn(Optional.of(source));
        when(objectStorageGateway.put(
                properties.snapshotBucket(),
                expectedKey,
                content,
                "application/json",
                java.util.Map.of(
                        "sha256", sha256(content),
                        "document-id", source.documentId().toString(),
                        "version-id", source.versionId().toString()
                )
        )).thenReturn(new ObjectStorageGateway.StoredObjectWriteResult(
                properties.snapshotBucket(),
                expectedKey,
                content.length,
                "etag"
        ));
        when(metadataRepository.save(any())).thenReturn(true);

        SnapshotProjectionService.ProjectionResult result = service.project(
                eventId,
                payload(source)
        );

        assertThat(result).isEqualTo(
                SnapshotProjectionService.ProjectionResult.CREATED
        );
        ArgumentCaptor<StoredObjectMetadata> metadata =
                ArgumentCaptor.forClass(StoredObjectMetadata.class);
        verify(metadataRepository).save(metadata.capture());
        assertThat(metadata.getValue().sourceEventId()).isEqualTo(eventId);
        assertThat(metadata.getValue().objectKey()).isEqualTo(expectedKey);
        assertThat(metadata.getValue().checksumSha256())
                .isEqualTo(sha256(content));
        assertThat(metadata.getValue().referenceId())
                .isEqualTo(source.versionId());
    }

    @Test
    void skipsObjectWriteWhenSourceEventWasAlreadyProjected() {
        UUID eventId = UUID.randomUUID();
        when(metadataRepository.existsBySourceEventId(eventId))
                .thenReturn(true);

        assertThat(service.project(eventId, "{}"))
                .isEqualTo(
                        SnapshotProjectionService.ProjectionResult.ALREADY_PROJECTED
                );

        verify(sourceRepository, never()).findByVersionId(any());
        verify(objectStorageGateway, never()).put(
                any(), any(), any(), any(), any()
        );
    }

    @Test
    void rejectsPayloadThatDoesNotMatchAuthoritativeVersion() {
        UUID eventId = UUID.randomUUID();
        SnapshotSource source = source();
        when(sourceRepository.findByVersionId(source.versionId()))
                .thenReturn(Optional.of(source));

        String mismatched = """
                {"workspaceId":"%s","documentId":"%s","versionId":"%s","versionNo":99}
                """.formatted(
                source.workspaceId(),
                source.documentId(),
                source.versionId()
        );

        assertThatThrownBy(() -> service.project(eventId, mismatched))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authoritative version data");
        verify(objectStorageGateway, never()).put(
                any(), any(), any(), any(), any()
        );
        verify(metadataRepository, never()).save(any());
    }

    @Test
    void doesNotRecordMetadataWhenObjectStorageFails() {
        UUID eventId = UUID.randomUUID();
        SnapshotSource source = source();
        when(sourceRepository.findByVersionId(source.versionId()))
                .thenReturn(Optional.of(source));
        when(objectStorageGateway.put(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("minio down"));

        assertThatThrownBy(() -> service.project(eventId, payload(source)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("minio down");
        verify(metadataRepository, never()).save(any());
    }

    private static SnapshotSource source() {
        return new SnapshotSource(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                2,
                "{\"blocks\":[{\"text\":\"snapshot\"}]}",
                UUID.randomUUID(),
                Instant.parse("2026-07-17T01:00:00Z")
        );
    }

    private static String payload(SnapshotSource source) {
        return """
                {"workspaceId":"%s","documentId":"%s","versionId":"%s","versionNo":%d}
                """.formatted(
                source.workspaceId(),
                source.documentId(),
                source.versionId(),
                source.versionNo()
        );
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(content)
        );
    }
}
