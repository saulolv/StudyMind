package com.contentservice.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * The production adapter behind the {@link BlobStore} seam. MinIO and S3 are the same adapter with
 * a different endpoint, so what is worth asserting is the request it builds, not which one answers.
 */
class S3BlobStoreTest {

    private final S3Client s3Client = mock(S3Client.class);
    private final BlobStore blobStore = new S3BlobStore(s3Client, "studymind");

    @Test
    void putsTheBytesUnderTheGivenKeyInTheConfiguredBucket() throws IOException {
        byte[] bytes = "%PDF-1.7 body".getBytes(StandardCharsets.UTF_8);

        blobStore.put("raw/u/c/lecture.pdf", bytes, "application/pdf");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(request.capture(), body.capture());

        assertThat(request.getValue().bucket()).isEqualTo("studymind");
        assertThat(request.getValue().key()).isEqualTo("raw/u/c/lecture.pdf");
        assertThat(request.getValue().contentType()).isEqualTo("application/pdf");
        assertThat(request.getValue().contentLength()).isEqualTo(bytes.length);
        assertThat(body.getValue().contentStreamProvider().newStream().readAllBytes()).isEqualTo(bytes);
    }

    @Test
    void doesNotSwallowAStorageFailure() {
        S3Client failing = mock(S3Client.class);
        RuntimeException boom = new RuntimeException("bucket is gone");
        org.mockito.Mockito.when(failing.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(boom);

        BlobStore store = new S3BlobStore(failing, "studymind");

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> store.put("k", new byte[] {1}, "application/pdf"))
                .isSameAs(boom);
    }
}
