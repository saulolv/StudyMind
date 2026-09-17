package com.contentservice.catalog;

import com.contentservice.domain.Content;
import com.contentservice.domain.ContentRepository;
import com.contentservice.events.ContentDeleted;
import com.contentservice.events.EventPublisher;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and lifecycle for content already ingested.
 *
 * <p>A concrete class, not an interface: nothing varies across this seam. Its only dependency that
 * a test would want to swap is the repository, and Spring Data repositories are already an
 * interface with a local stand-in. A port here would be indirection, not a seam.
 *
 * <p>Every method takes the owning {@code userId} as its first argument, so there is no read path
 * in this service that is not scoped to a user.
 */
@Service
public class ContentCatalog {

    private final ContentRepository repository;
    private final EventPublisher eventPublisher;

    public ContentCatalog(ContentRepository repository, EventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public Page<Content> list(UUID userId, Pageable pageable) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Content require(UUID userId, UUID contentId) {
        return repository
                .findByContentIdAndUserId(contentId, userId)
                .orElseThrow(() -> new ContentNotFoundException(contentId));
    }

    /** Returns only the requested ids that belong to {@code userId}; unknown ids are simply absent. */
    @Transactional(readOnly = true)
    public List<Content> statusesOf(UUID userId, List<UUID> contentIds) {
        if (contentIds.isEmpty()) {
            return List.of();
        }
        return repository.findByUserIdAndContentIdIn(userId, contentIds);
    }

    /**
     * Removes the row and announces the cleanup. Blob and vector removal are the consumers' job,
     * which is what the 202 on the HTTP endpoint means.
     */
    @Transactional
    public void delete(UUID userId, UUID contentId) {
        Content content = repository
                .findByContentIdAndUserId(contentId, userId)
                .orElseThrow(() -> new ContentNotFoundException(contentId));
        repository.delete(content);
        eventPublisher.publish(
                new ContentDeleted(content.contentId(), content.userId(), content.type(), content.storagePath()));
    }
}
