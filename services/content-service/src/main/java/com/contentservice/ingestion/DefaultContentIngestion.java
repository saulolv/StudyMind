package com.contentservice.ingestion;

import com.contentservice.domain.Content;
import com.contentservice.domain.ContentRepository;
import com.contentservice.events.ContentSubmitted;
import com.contentservice.events.EventPublisher;
import com.contentservice.ingestion.ContentSource.PdfUpload;
import com.contentservice.ingestion.ContentSource.YouTubeLink;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DefaultContentIngestion implements ContentIngestion {

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final ContentRepository repository;
    private final BlobStore blobStore;
    private final EventPublisher eventPublisher;
    private final Clock clock;
    private final long maxPdfBytes;

    DefaultContentIngestion(
            ContentRepository repository,
            BlobStore blobStore,
            EventPublisher eventPublisher,
            Clock clock,
            @Value("${studymind.ingestion.max-pdf-bytes}") long maxPdfBytes) {
        this.repository = repository;
        this.blobStore = blobStore;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.maxPdfBytes = maxPdfBytes;
    }

    @Override
    @Transactional
    public Content ingest(UUID userId, ContentSource source) {
        UUID contentId = UUID.randomUUID();
        Instant now = clock.instant();
        return switch (source) {
            case PdfUpload pdf -> ingestPdf(userId, contentId, pdf, now);
            case YouTubeLink link -> ingestYouTube(userId, contentId, link, now);
        };
    }

    private Content ingestPdf(UUID userId, UUID contentId, PdfUpload upload, Instant now) {
        String fileName = sanitizeFileName(upload.fileName());
        byte[] bytes = upload.bytes();

        if (bytes == null || bytes.length == 0) {
            throw new InvalidContentSourceException("Uploaded file is empty");
        }
        if (bytes.length > maxPdfBytes) {
            throw new InvalidContentSourceException(
                    "Uploaded file exceeds the maximum of " + maxPdfBytes + " bytes");
        }
        // The declared content type is a client-supplied hint; the magic bytes are the real check.
        if (!startsWithPdfMagic(bytes)) {
            throw new InvalidContentSourceException("Uploaded file is not a PDF");
        }

        String storagePath = "raw/%s/%s/%s".formatted(userId, contentId, fileName);
        // Bytes land before the event does, so a worker consuming it can always read storage_path.
        blobStore.put(storagePath, bytes, "application/pdf");

        Content content = repository.save(Content.pdf(contentId, userId, fileName, storagePath, now));
        eventPublisher.publish(ContentSubmitted.pdf(contentId, userId, storagePath, fileName));
        return content;
    }

    private Content ingestYouTube(UUID userId, UUID contentId, YouTubeLink link, Instant now) {
        String videoId = YouTubeLinks.videoId(link.url())
                .orElseThrow(() -> new InvalidContentSourceException(
                        "Not a supported YouTube video URL: " + link.url()));

        // Canonicalised so the same video submitted via youtu.be, /shorts and a tracking-laden
        // watch URL is stored, and deduplicated downstream, as one source_url.
        String sourceUrl = "https://www.youtube.com/watch?v=" + videoId;

        Content content = repository.save(Content.video(contentId, userId, sourceUrl, now));
        eventPublisher.publish(ContentSubmitted.video(contentId, userId, sourceUrl));
        return content;
    }

    private static boolean startsWithPdfMagic(byte[] bytes) {
        return bytes.length >= PDF_MAGIC.length
                && Arrays.equals(Arrays.copyOfRange(bytes, 0, PDF_MAGIC.length), PDF_MAGIC);
    }

    private static String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new InvalidContentSourceException("File name is required");
        }
        String base = fileName.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        base = base.replaceAll("[^A-Za-z0-9._-]", "_");
        if (base.isBlank() || base.equals(".") || base.equals("..")) {
            throw new InvalidContentSourceException("File name is not usable: " + fileName);
        }
        return base.length() > 200 ? base.substring(base.length() - 200) : base;
    }
}
