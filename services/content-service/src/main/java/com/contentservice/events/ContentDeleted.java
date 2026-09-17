package com.contentservice.events;

import com.contentservice.domain.ContentType;
import java.util.UUID;

/**
 * Carries the asynchronous cleanup that {@code DELETE /contents/{id}} promises with its 202.
 * The old OpenAPI declared that response with no event behind it.
 */
public record ContentDeleted(
        UUID contentId,
        UUID userId,
        ContentType type,
        String storagePath) implements EventPayload {
}
