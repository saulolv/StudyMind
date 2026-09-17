package com.contentservice.api.dto;

import com.contentservice.domain.Content;
import java.time.Instant;
import java.util.UUID;

public record ContentDetailsResponse(
        UUID contentId,
        String type,
        String status,
        String fileName,
        String sourceUrl,
        String title,
        Integer durationSeconds,
        String storagePath,
        Instant createdAt,
        Instant updatedAt) {

    public static ContentDetailsResponse of(Content content) {
        return new ContentDetailsResponse(
                content.contentId(),
                content.type().name(),
                content.status().name(),
                content.fileName(),
                content.sourceUrl(),
                content.title(),
                content.durationSeconds(),
                content.storagePath(),
                content.createdAt(),
                content.updatedAt());
    }
}
