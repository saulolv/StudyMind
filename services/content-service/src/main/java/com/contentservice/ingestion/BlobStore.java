package com.contentservice.ingestion;

/**
 * Internal seam of the ingestion module - package-private on purpose: it is not part of
 * {@link ContentIngestion}'s interface, it exists so ingestion can be tested without MinIO.
 *
 * <p>Note this is <em>not</em> here to abstract MinIO-today-S3-tomorrow. MinIO is S3-compatible,
 * so those are the same adapter with a different endpoint. The second adapter that justifies the
 * seam is the in-memory one used by tests.
 */
interface BlobStore {

    void put(String key, byte[] bytes, String contentType);
}
