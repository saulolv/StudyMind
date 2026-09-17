package com.contentservice.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentRepository extends JpaRepository<Content, UUID> {

    Page<Content> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<Content> findByContentIdAndUserId(UUID contentId, UUID userId);

    List<Content> findByUserIdAndContentIdIn(UUID userId, List<UUID> contentIds);
}
