package com.contentservice.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.contentservice.domain.Content;
import com.contentservice.domain.ContentRepository;
import com.contentservice.domain.ContentType;
import com.contentservice.events.ContentDeleted;
import com.contentservice.events.EventPayload;
import com.contentservice.events.EventPublisher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Every assertion here is about the same property: no read or write in this service reaches a row
 * that the calling user does not own. The catalog is the only place that could break it.
 */
class ContentCatalogTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID STRANGER = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID CONTENT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2026-09-17T12:00:00Z");

    private final List<EventPayload> published = new ArrayList<>();
    private ContentRepository repository;
    private ContentCatalog catalog;

    @BeforeEach
    void setUp() {
        repository = mock(ContentRepository.class);
        catalog = new ContentCatalog(repository, published::add);
    }

    @Test
    void listsOnlyTheOwnersContents() {
        Content content = pdf();
        Pageable page = PageRequest.of(0, 20);
        when(repository.findByUserIdOrderByCreatedAtDesc(OWNER, page))
                .thenReturn(new PageImpl<>(List.of(content), page, 1));

        assertThat(catalog.list(OWNER, page).getContent()).containsExactly(content);
    }

    @Test
    void returnsAContentTheUserOwns() {
        Content content = pdf();
        when(repository.findByContentIdAndUserId(CONTENT, OWNER)).thenReturn(Optional.of(content));

        assertThat(catalog.require(OWNER, CONTENT)).isSameAs(content);
    }

    @Test
    void reportsSomeoneElsesContentAsNotFoundRatherThanForbidden() {
        when(repository.findByContentIdAndUserId(CONTENT, STRANGER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalog.require(STRANGER, CONTENT))
                .isInstanceOf(ContentNotFoundException.class)
                .hasMessageContaining(CONTENT.toString());
    }

    @Test
    void asksTheDatabaseNothingWhenNoIdsWereRequested() {
        assertThat(catalog.statusesOf(OWNER, List.of())).isEmpty();

        verify(repository, never()).findByUserIdAndContentIdIn(any(), any());
    }

    @Test
    void reportsStatusesOnlyForIdsTheUserOwns() {
        Content content = pdf();
        List<UUID> asked = List.of(CONTENT, STRANGER);
        when(repository.findByUserIdAndContentIdIn(OWNER, asked)).thenReturn(List.of(content));

        assertThat(catalog.statusesOf(OWNER, asked)).containsExactly(content);
    }

    @Test
    void removesTheRowAndAnnouncesTheCleanupItLeavesBehind() {
        Content content = pdf();
        when(repository.findByContentIdAndUserId(CONTENT, OWNER)).thenReturn(Optional.of(content));

        catalog.delete(OWNER, CONTENT);

        verify(repository).delete(content);
        assertThat(published).singleElement().isEqualTo(
                new ContentDeleted(CONTENT, OWNER, ContentType.PDF, "raw/u/c/lecture.pdf"));
    }

    @Test
    void deletesNothingAndAnnouncesNothingForAContentTheUserDoesNotOwn() {
        when(repository.findByContentIdAndUserId(CONTENT, STRANGER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalog.delete(STRANGER, CONTENT))
                .isInstanceOf(ContentNotFoundException.class);

        verify(repository, never()).delete(any(Content.class));
        assertThat(published).isEmpty();
    }

    private static Content pdf() {
        return Content.pdf(CONTENT, OWNER, "lecture.pdf", "raw/u/c/lecture.pdf", NOW);
    }
}
