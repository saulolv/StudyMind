package com.contentservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "content")
public class Content {

    @Id
    @Column(name = "content_id", nullable = false, updatable = false)
    private UUID contentId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 16)
    private ContentType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ContentStatus status;

    @Column(name = "file_name", length = 512)
    private String fileName;

    @Column(name = "storage_path", length = 1024)
    private String storagePath;

    @Column(name = "source_url", length = 2048)
    private String sourceUrl;

    @Column(name = "title", length = 512)
    private String title;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Content() {
        // for JPA
    }

    private Content(UUID contentId, UUID userId, ContentType type, Instant now) {
        this.contentId = contentId;
        this.userId = userId;
        this.type = type;
        this.status = ContentStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Content pdf(UUID contentId, UUID userId, String fileName, String storagePath, Instant now) {
        Content content = new Content(contentId, userId, ContentType.PDF, now);
        content.fileName = fileName;
        content.storagePath = storagePath;
        return content;
    }

    public static Content video(UUID contentId, UUID userId, String sourceUrl, Instant now) {
        Content content = new Content(contentId, userId, ContentType.VIDEO, now);
        content.sourceUrl = sourceUrl;
        return content;
    }

    public void markStatus(ContentStatus newStatus, Instant now) {
        this.status = newStatus;
        this.updatedAt = now;
    }

    public UUID contentId() {
        return contentId;
    }

    public UUID userId() {
        return userId;
    }

    public ContentType type() {
        return type;
    }

    public ContentStatus status() {
        return status;
    }

    public String fileName() {
        return fileName;
    }

    public String storagePath() {
        return storagePath;
    }

    public String sourceUrl() {
        return sourceUrl;
    }

    public String title() {
        return title;
    }

    public Integer durationSeconds() {
        return durationSeconds;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
