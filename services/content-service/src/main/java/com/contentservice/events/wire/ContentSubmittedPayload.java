// Generated from contracts/events/v1/content-submitted.v1.schema.json by tools/contracts. Do not edit.
// Regenerate with: python -m studymind_contracts codegen
package com.contentservice.events.wire;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Payload of the ContentSubmitted event, generated from
 * {@code contracts/events/v1/content-submitted.v1.schema.json}.
 *
 * <p>Built through {@link #builder()} rather than positionally: one named setter per
 * contract field is what makes a renamed or dropped field a compile error.
 */
public record ContentSubmittedPayload(
        UUID contentId,
        UUID userId,
        String type,
        String storagePath,
        String fileName,
        String sourceUrl) {

    public static final String EVENT_TYPE = "ContentSubmitted";

    public ContentSubmittedPayload {
        Objects.requireNonNull(contentId, "content_id is required by the contract");
        Objects.requireNonNull(userId, "user_id is required by the contract");
        Objects.requireNonNull(type, "type is required by the contract");
        if (!List.of("PDF", "VIDEO").contains(type)) {
            throw new IllegalArgumentException(
                    "type is not a value the contract allows: " + type);
        }
    }

    /** The snake_case body the contract describes: optional fields absent, not null. */
    public Map<String, Object> toWireMap() {
        Map<String, Object> wire = new LinkedHashMap<>();
        wire.put("content_id", contentId.toString());
        wire.put("user_id", userId.toString());
        wire.put("type", type);
        if (storagePath != null) {
            wire.put("storage_path", storagePath);
        }
        if (fileName != null) {
            wire.put("file_name", fileName);
        }
        if (sourceUrl != null) {
            wire.put("source_url", sourceUrl);
        }
        return wire;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** One setter per contract field, named after it. That naming is the whole point. */
    public static final class Builder {

        private UUID contentId;
        private UUID userId;
        private String type;
        private String storagePath;
        private String fileName;
        private String sourceUrl;

        public Builder contentId(UUID contentId) {
            this.contentId = contentId;
            return this;
        }

        public Builder userId(UUID userId) {
            this.userId = userId;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder storagePath(String storagePath) {
            this.storagePath = storagePath;
            return this;
        }

        public Builder fileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public Builder sourceUrl(String sourceUrl) {
            this.sourceUrl = sourceUrl;
            return this;
        }

        public ContentSubmittedPayload build() {
            return new ContentSubmittedPayload(contentId, userId, type, storagePath, fileName, sourceUrl);
        }
    }
}
