package com.contentservice.api.dto;

import com.contentservice.domain.Content;
import java.util.UUID;

public record ContentSummaryResponse(
        UUID contentId,
        String type,
        String status,
        String fileName,
        String sourceUrl,
        String title,
        Integer durationSeconds) {

    public static ContentSummaryResponse of(Content content) {
        return new ContentSummaryResponse(
                content.contentId(),
                content.type().name(),
                content.status().name(),
                content.fileName(),
                content.sourceUrl(),
                content.title(),
                content.durationSeconds());
    }
}
