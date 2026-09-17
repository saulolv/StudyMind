package com.contentservice.ingestion;

import java.net.URI;

/**
 * The two adapters at the ingestion seam.
 *
 * <p>This is what used to justify two services. PDF upload and YouTube submission are two ways of
 * naming a source for the same thing — a unit of content owned by a user — so they belong behind
 * one seam, not behind two deployables with duplicated ownership endpoints.
 */
public sealed interface ContentSource {

    record PdfUpload(String fileName, byte[] bytes, String declaredContentType) implements ContentSource {
    }

    record YouTubeLink(URI url) implements ContentSource {
    }
}
