package com.contentservice.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.contentservice.domain.Content;
import com.contentservice.domain.ContentRepository;
import com.contentservice.domain.ContentStatus;
import com.contentservice.domain.ContentType;
import com.contentservice.events.ContentSubmitted;
import com.contentservice.events.EventPayload;
import com.contentservice.events.EventPublisher;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests sit at the {@link ContentIngestion} interface: they exercise ingestion the way a controller
 * does and assert on what a caller and a downstream worker can observe. The blob store is an
 * in-memory adapter at an internal seam, which is why no MinIO is needed here.
 */
class DefaultContentIngestionTest {

    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final long MAX_PDF_BYTES = 1_000;

    private final Map<String, byte[]> blobs = new HashMap<>();
    private final List<EventPayload> published = new ArrayList<>();
    private ContentRepository repository;
    private ContentIngestion ingestion;

    @BeforeEach
    void setUp() {
        repository = mock(ContentRepository.class);
        when(repository.save(any(Content.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BlobStore blobStore = (key, bytes, contentType) -> blobs.put(key, bytes);
        EventPublisher eventPublisher = published::add;

        ingestion = new DefaultContentIngestion(
                repository,
                blobStore,
                eventPublisher,
                Clock.fixed(Instant.parse("2026-09-17T12:00:00Z"), ZoneOffset.UTC),
                MAX_PDF_BYTES);
    }

    @Test
    void storesPdfBytesAndAnnouncesTheSubmission() {
        Content content = ingestion.ingest(USER, pdf("lecture.pdf", "%PDF-1.7 body"));

        assertThat(content.type()).isEqualTo(ContentType.PDF);
        assertThat(content.status()).isEqualTo(ContentStatus.PENDING);
        assertThat(content.userId()).isEqualTo(USER);
        assertThat(content.storagePath()).isEqualTo("raw/%s/%s/lecture.pdf".formatted(USER, content.contentId()));
        assertThat(blobs).containsOnlyKeys(content.storagePath());

        assertThat(published).singleElement().isInstanceOfSatisfying(ContentSubmitted.class, event -> {
            assertThat(event.contentId()).isEqualTo(content.contentId());
            assertThat(event.userId()).isEqualTo(USER);
            assertThat(event.type()).isEqualTo(ContentType.PDF);
            assertThat(event.storagePath()).isEqualTo(content.storagePath());
            assertThat(event.fileName()).isEqualTo("lecture.pdf");
            assertThat(event.sourceUrl()).isNull();
        });
    }

    @Test
    void writesBytesBeforePublishingSoAConsumerCanAlwaysReadStoragePath() {
        List<String> order = new ArrayList<>();
        BlobStore recordingStore = (key, bytes, contentType) -> order.add("stored");
        EventPublisher recordingPublisher = payload -> order.add("published");
        ContentIngestion subject = new DefaultContentIngestion(
                repository,
                recordingStore,
                recordingPublisher,
                Clock.systemUTC(),
                MAX_PDF_BYTES);

        subject.ingest(USER, pdf("a.pdf", "%PDF-1.4 x"));

        assertThat(order).containsExactly("stored", "published");
    }

    @Test
    void rejectsAFileThatIsNotActuallyAPdfEvenWhenTheClientSaysItIs() {
        assertThatThrownBy(() -> ingestion.ingest(USER, pdf("trojan.pdf", "<html>nope</html>")))
                .isInstanceOf(InvalidContentSourceException.class)
                .hasMessageContaining("not a PDF");

        assertThat(blobs).isEmpty();
        assertThat(published).isEmpty();
    }

    @Test
    void rejectsAnOversizedUpload() {
        String body = "%PDF-1.7 " + "x".repeat((int) MAX_PDF_BYTES);

        assertThatThrownBy(() -> ingestion.ingest(USER, pdf("big.pdf", body)))
                .isInstanceOf(InvalidContentSourceException.class)
                .hasMessageContaining("maximum");

        assertThat(blobs).isEmpty();
        assertThat(published).isEmpty();
    }

    @Test
    void rejectsAnEmptyUpload() {
        assertThatThrownBy(() -> ingestion.ingest(USER, pdf("empty.pdf", "")))
                .isInstanceOf(InvalidContentSourceException.class);

        assertThat(published).isEmpty();
    }

    @Test
    void neverLetsAFileNameEscapeTheUsersPrefix() {
        Content content = ingestion.ingest(USER, pdf("../../../etc/passwd.pdf", "%PDF-1.4 x"));

        assertThat(content.storagePath()).startsWith("raw/%s/".formatted(USER));
        assertThat(content.storagePath()).doesNotContain("..");
        assertThat(content.fileName()).isEqualTo("passwd.pdf");
    }

    @Test
    void canonicalisesEveryAcceptedYouTubeUrlToOneSourceUrl() {
        Content fromShortLink = ingestion.ingest(USER, youtube("https://youtu.be/dQw4w9WgXcQ?t=42"));
        Content fromWatchLink =
                ingestion.ingest(USER, youtube("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL9"));

        assertThat(fromShortLink.sourceUrl())
                .isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
                .isEqualTo(fromWatchLink.sourceUrl());
        assertThat(fromShortLink.type()).isEqualTo(ContentType.VIDEO);
        assertThat(fromShortLink.status()).isEqualTo(ContentStatus.PENDING);
        assertThat(fromShortLink.storagePath()).isNull();
    }

    @Test
    void announcesAVideoSubmissionWithoutTouchingStorage() {
        Content content = ingestion.ingest(USER, youtube("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));

        assertThat(blobs).isEmpty();
        assertThat(published).singleElement().isInstanceOfSatisfying(ContentSubmitted.class, event -> {
            assertThat(event.type()).isEqualTo(ContentType.VIDEO);
            assertThat(event.sourceUrl()).isEqualTo(content.sourceUrl());
            assertThat(event.storagePath()).isNull();
            assertThat(event.fileName()).isNull();
        });
    }

    @Test
    void rejectsANonYouTubeUrl() {
        assertThatThrownBy(() -> ingestion.ingest(USER, youtube("https://vimeo.com/123456789")))
                .isInstanceOf(InvalidContentSourceException.class);

        assertThat(published).isEmpty();
    }

    private static ContentSource pdf(String fileName, String body) {
        return new ContentSource.PdfUpload(
                fileName, body.getBytes(StandardCharsets.UTF_8), "application/pdf");
    }

    private static ContentSource youtube(String url) {
        return new ContentSource.YouTubeLink(URI.create(url));
    }
}
