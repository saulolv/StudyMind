package com.contentservice.events;

import com.contentservice.domain.ContentType;
import java.util.UUID;

/**
 * Replaces the old {@code ContentUploaded} (document-service) and {@code VideoSubmitted}
 * (video-service) events with one event carrying a {@link ContentType} discriminator, matching
 * what {@code ChunksCreated} and {@code ContentIndexed} already did downstream.
 *
 * <p>{@code storagePath}/{@code fileName} are set for {@link ContentType#PDF};
 * {@code sourceUrl} is set for {@link ContentType#VIDEO}. The JSON Schema enforces this with
 * an {@code if/then} on {@code type}.
 */
public record ContentSubmitted(
        UUID contentId,
        UUID userId,
        ContentType type,
        String storagePath,
        String fileName,
        String sourceUrl) implements EventPayload {

    public static ContentSubmitted pdf(UUID contentId, UUID userId, String storagePath, String fileName) {
        return new ContentSubmitted(contentId, userId, ContentType.PDF, storagePath, fileName, null);
    }

    public static ContentSubmitted video(UUID contentId, UUID userId, String sourceUrl) {
        return new ContentSubmitted(contentId, userId, ContentType.VIDEO, null, null, sourceUrl);
    }
}
