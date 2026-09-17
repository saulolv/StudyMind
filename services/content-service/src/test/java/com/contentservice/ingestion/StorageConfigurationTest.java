package com.contentservice.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

class StorageConfigurationTest {

    /**
     * Path-style addressing is what makes one adapter serve both: MinIO has no bucket subdomains,
     * so a virtual-hosted client works against S3 and fails against dev infrastructure.
     */
    @Test
    void buildsAPathStyleClientPointedAtTheConfiguredEndpoint() {
        try (S3Client client = new StorageConfiguration()
                .s3Client("http://localhost:9000", "us-east-1", "studymind", "studymind")) {
            assertThat(client.serviceClientConfiguration().endpointOverride())
                    .contains(URI.create("http://localhost:9000"));
            assertThat(client.serviceClientConfiguration().region()).isEqualTo(Region.of("us-east-1"));
        }
    }
}
