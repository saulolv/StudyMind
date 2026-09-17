package com.contentservice.domain;

/**
 * One lifecycle for both content types.
 *
 * <p>The old video-service carried a separate {@code transcript_status} alongside {@code status};
 * under a unified content model transcription is simply the {@link #PROCESSING} stage of a
 * {@link ContentType#VIDEO}, so the second field is gone.
 */
public enum ContentStatus {
    PENDING,
    PROCESSING,
    INDEXED,
    FAILED
}
