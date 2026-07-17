package com.devcollab.worker.objectstorage;

import java.util.Map;

public interface ObjectStorageGateway {

    StoredObjectWriteResult put(
            String bucket,
            String objectKey,
            byte[] content,
            String contentType,
            Map<String, String> metadata
    );

    record StoredObjectWriteResult(
            String bucket,
            String objectKey,
            long sizeBytes,
            String etag
    ) {
    }
}
