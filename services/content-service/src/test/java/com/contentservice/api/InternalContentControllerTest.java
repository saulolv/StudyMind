package com.contentservice.api;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contentservice.catalog.ContentCatalog;
import com.contentservice.domain.Content;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The single endpoint that replaced document-service's and video-service's
 * {@code /internal/{id}/ownership}. The behaviour worth pinning down is that it answers about the
 * authenticated caller only, and that ids it cannot vouch for are absent rather than false.
 */
@WebMvcTest(InternalContentController.class)
class InternalContentControllerTest {

    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MINE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SOMEONE_ELSES = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant NOW = Instant.parse("2026-09-17T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ContentCatalog catalog;

    @Test
    void reportsReadinessForEachIdTheCallerOwns() throws Exception {
        when(catalog.statusesOf(eq(USER), anyList()))
                .thenReturn(List.of(Content.pdf(MINE, USER, "lecture.pdf", "raw/u/c/lecture.pdf", NOW)));

        mockMvc.perform(get("/internal/contents")
                        .param("ids", MINE + "," + SOMEONE_ELSES)
                        .header("X-User-Id", USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].content_id").value(MINE.toString()))
                .andExpect(jsonPath("$.items[0].type").value("PDF"))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"));
    }

    /** The user id is no longer a query parameter, so this endpoint cannot be asked about anyone else. */
    @Test
    void scopesTheQuestionToTheAuthenticatedUser() throws Exception {
        when(catalog.statusesOf(eq(USER), anyList())).thenReturn(List.of());

        mockMvc.perform(get("/internal/contents").param("ids", MINE.toString()).header("X-User-Id", USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());

        verify(catalog).statusesOf(USER, List.of(MINE));
    }

    @Test
    void refusesAnUnauthenticatedCaller() throws Exception {
        mockMvc.perform(get("/internal/contents").param("ids", MINE.toString()))
                .andExpect(status().isUnauthorized());
    }
}
