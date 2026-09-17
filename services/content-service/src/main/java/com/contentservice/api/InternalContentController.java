package com.contentservice.api;

import com.contentservice.api.dto.InternalContentStatus;
import com.contentservice.api.dto.InternalContentStatusResponse;
import com.contentservice.catalog.ContentCatalog;
import com.contentservice.web.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces the two {@code /internal/{id}/ownership} endpoints that document-service and
 * video-service each carried.
 *
 * <p>Two things changed beyond de-duplication. It answers with readiness for a batch of ids rather
 * than a boolean for one, because "is this owned?" pushed the decision back to the caller and a
 * caller actually needs to know whether a content is indexed yet. And the user id now arrives as
 * the authenticated {@code X-User-Id} header instead of a query parameter, so this endpoint can no
 * longer be asked about an arbitrary user.
 *
 * <p>This endpoint is a candidate for deletion once chat-llm-service exists: if retrieval filters
 * the vector search by user, a caller asking about content it does not own simply gets no chunks,
 * and the check is redundant. It survives for now only to report readiness.
 */
@RestController
@RequestMapping("/internal/contents")
class InternalContentController {

    private final ContentCatalog catalog;

    InternalContentController(ContentCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    InternalContentStatusResponse statuses(
            @CurrentUser UUID userId, @RequestParam List<UUID> ids) {
        List<InternalContentStatus> items =
                catalog.statusesOf(userId, ids).stream().map(InternalContentStatus::of).toList();
        return new InternalContentStatusResponse(items);
    }
}
