package com.contentservice.api.dto;

import com.contentservice.domain.Content;
import java.util.UUID;

public record InternalContentStatus(UUID contentId, String type, String status) {

    public static InternalContentStatus of(Content content) {
        return new InternalContentStatus(
                content.contentId(), content.type().name(), content.status().name());
    }
}
