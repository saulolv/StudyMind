package com.contentservice.api.dto;

import com.contentservice.domain.Content;
import java.util.UUID;

public record ContentAcceptedResponse(UUID contentId, String type, String status) {

    public static ContentAcceptedResponse of(Content content) {
        return new ContentAcceptedResponse(
                content.contentId(), content.type().name(), content.status().name());
    }
}
