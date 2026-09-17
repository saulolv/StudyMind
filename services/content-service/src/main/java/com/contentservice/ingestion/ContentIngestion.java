package com.contentservice.ingestion;

import com.contentservice.domain.Content;
import java.util.UUID;

/**
 * Accepts a unit of content from any source and makes it durable, owned, and announced.
 *
 * <p>Invariants a caller can rely on, and does not have to re-establish:
 * <ul>
 *   <li>the returned {@link Content} is persisted and owned by {@code userId};</li>
 *   <li>its status is {@code PENDING} — ingestion never blocks on processing;</li>
 *   <li>exactly one {@code ContentSubmitted} event has been published for it;</li>
 *   <li>for a PDF, the bytes are in object storage before the event is published, so a worker
 *       consuming the event can always read {@code storage_path}.</li>
 * </ul>
 *
 * <p>Error mode: {@link InvalidContentSourceException} for anything the caller could have
 * prevented (not a PDF, unsupported URL, oversized upload).
 */
public interface ContentIngestion {

    Content ingest(UUID userId, ContentSource source);
}
