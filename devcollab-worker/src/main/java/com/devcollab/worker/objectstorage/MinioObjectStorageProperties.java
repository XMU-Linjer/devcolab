package com.devcollab.worker.objectstorage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "devcollab.object-storage.minio")
public record MinioObjectStorageProperties(
        boolean enabled,
        String endpoint,
        String accessKey,
        String secretKey,
        String snapshotBucket
) {
    public MinioObjectStorageProperties {
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = "http://localhost:9000";
        }
        if (accessKey == null || accessKey.isBlank()) {
            accessKey = "devcollab";
        }
        if (secretKey == null || secretKey.isBlank()) {
            secretKey = "devcollab-minio-password";
        }
        if (snapshotBucket == null || snapshotBucket.isBlank()) {
            snapshotBucket = "devcollab-snapshots";
        }
    }
}
