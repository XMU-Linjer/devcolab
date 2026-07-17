package com.devcollab.worker.objectstorage;

import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MinioObjectStorageProperties.class)
public class MinioObjectStorageConfig {

    @Bean
    @ConditionalOnProperty(
            name = "devcollab.object-storage.minio.enabled",
            havingValue = "true"
    )
    public MinioClient minioClient(MinioObjectStorageProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }
}
