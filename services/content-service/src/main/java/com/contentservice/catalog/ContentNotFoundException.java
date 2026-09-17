package com.contentservice.catalog;

import java.util.UUID;

public class ContentNotFoundException extends RuntimeException {

    public ContentNotFoundException(UUID contentId) {
        super("Content not found: " + contentId);
    }
}
