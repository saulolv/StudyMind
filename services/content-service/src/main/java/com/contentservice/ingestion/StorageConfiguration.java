package com.contentservice.ingestion;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
class StorageConfiguration {

    /** MinIO in dev, S3 in prod: one adapter, different endpoint and credentials. */
    @Bean
    @ConditionalOnMissingBean
    S3Client s3Client(
            @Value("${studymind.storage.endpoint}") String endpoint,
            @Value("${studymind.storage.region}") String region,
            @Value("${studymind.storage.access-key}") String accessKey,
            @Value("${studymind.storage.secret-key}") String secretKey) {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(
                        StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true)
                .build();
    }
}
