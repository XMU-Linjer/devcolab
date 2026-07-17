package com.devcollab.worker.objectstorage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.Map;

@Component
@ConditionalOnProperty(
        name = "devcollab.object-storage.minio.enabled",
        havingValue = "true"
)
public class MinioObjectStorageGateway implements ObjectStorageGateway {

    private final MinioClient minioClient;

    public MinioObjectStorageGateway(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @Override
    public StoredObjectWriteResult put(
            String bucket,
            String objectKey,
            byte[] content,
            String contentType,
            Map<String, String> metadata
    ) {
        try {
            ensureBucket(bucket);
            var response = minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(
                                    new ByteArrayInputStream(content),
                                    (long) content.length,
                                    -1L
                            )
                            .contentType(contentType)
                            .userMetadata(metadata)
                            .build()
            );
            return new StoredObjectWriteResult(
                    bucket,
                    objectKey,
                    content.length,
                    response.etag()
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "MinIO object write failed: bucket=" + bucket
                            + ", objectKey=" + objectKey,
                    exception
            );
        }
    }

    private void ensureBucket(String bucket) throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucket).build()
        );
        if (!exists) {
            try {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucket).build()
                );
            } catch (Exception exception) {
                if (!minioClient.bucketExists(
                        BucketExistsArgs.builder().bucket(bucket).build()
                )) {
                    throw exception;
                }
            }
        }
    }
}
