package com.contentservice.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContentTest {

    private static final UUID CONTENT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-09-17T12:00:00Z");

    @Test
    void aPdfCarriesItsBytesAndNoSourceUrl() {
        Content content = Content.pdf(CONTENT, USER, "lecture.pdf", "raw/u/c/lecture.pdf", NOW);

        assertThat(content.contentId()).isEqualTo(CONTENT);
        assertThat(content.userId()).isEqualTo(USER);
        assertThat(content.type()).isEqualTo(ContentType.PDF);
        assertThat(content.status()).isEqualTo(ContentStatus.PENDING);
        assertThat(content.fileName()).isEqualTo("lecture.pdf");
        assertThat(content.storagePath()).isEqualTo("raw/u/c/lecture.pdf");
        assertThat(content.sourceUrl()).isNull();
        assertThat(content.title()).isNull();
        assertThat(content.durationSeconds()).isNull();
        assertThat(content.createdAt()).isEqualTo(NOW);
        assertThat(content.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void aVideoCarriesItsUrlAndNoStoragePath() {
        Content content = Content.video(CONTENT, USER, "https://www.youtube.com/watch?v=dQw4w9WgXcQ", NOW);

        assertThat(content.type()).isEqualTo(ContentType.VIDEO);
        assertThat(content.status()).isEqualTo(ContentStatus.PENDING);
        assertThat(content.sourceUrl()).isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        assertThat(content.storagePath()).isNull();
        assertThat(content.fileName()).isNull();
    }

    /**
     * Transcription is the PROCESSING stage of a VIDEO rather than a second status column, so the
     * two content types walk the same four states through this one method.
     */
    @Test
    void aStatusChangeMovesUpdatedAtAndLeavesCreatedAtAlone() {
        Content content = Content.video(CONTENT, USER, "https://www.youtube.com/watch?v=dQw4w9WgXcQ", NOW);
        Instant later = NOW.plusSeconds(90);

        content.markStatus(ContentStatus.PROCESSING, later);

        assertThat(content.status()).isEqualTo(ContentStatus.PROCESSING);
        assertThat(content.updatedAt()).isEqualTo(later);
        assertThat(content.createdAt()).isEqualTo(NOW);
    }

    /** Hibernate instantiates the entity itself; without this constructor the mapping fails at boot. */
    @Test
    void exposesTheNoArgConstructorJpaRequires() throws Exception {
        Constructor<Content> constructor = Content.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThat(constructor.newInstance()).isNotNull();
    }
}
