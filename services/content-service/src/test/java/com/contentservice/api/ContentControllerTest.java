package com.contentservice.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contentservice.catalog.ContentCatalog;
import com.contentservice.catalog.ContentNotFoundException;
import com.contentservice.domain.Content;
import com.contentservice.ingestion.ContentIngestion;
import com.contentservice.ingestion.ContentSource;
import com.contentservice.ingestion.InvalidContentSourceException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ContentController.class)
class ContentControllerTest {

    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CONTENT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2026-09-17T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ContentIngestion ingestion;

    @MockitoBean
    private ContentCatalog catalog;

    @Test
    void multipartGoesToTheIngestionSeamAsAPdfSource() throws Exception {
        when(ingestion.ingest(eq(USER), any(ContentSource.PdfUpload.class))).thenReturn(pdfContent());

        mockMvc.perform(multipart("/contents")
                        .file(new MockMultipartFile(
                                "file",
                                "lecture.pdf",
                                "application/pdf",
                                "%PDF-1.7 body".getBytes(StandardCharsets.UTF_8)))
                        .header("X-User-Id", USER))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.content_id").value(CONTENT.toString()))
                .andExpect(jsonPath("$.type").value("PDF"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void jsonOnTheSamePathGoesToTheIngestionSeamAsAYouTubeSource() throws Exception {
        when(ingestion.ingest(eq(USER), any(ContentSource.YouTubeLink.class))).thenReturn(videoContent());

        mockMvc.perform(post("/contents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://www.youtube.com/watch?v=dQw4w9WgXcQ\"}")
                        .header("X-User-Id", USER))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.type").value("VIDEO"));
    }

    @Test
    void refusesEveryRequestThatArrivesWithoutAnAuthenticatedUser() throws Exception {
        mockMvc.perform(post("/contents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://www.youtube.com/watch?v=dQw4w9WgXcQ\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/contents")).andExpect(status().isUnauthorized());

        verify(ingestion, never()).ingest(any(), any());
    }

    @Test
    void reportsAnInvalidSourceAsProblemDetails() throws Exception {
        when(ingestion.ingest(any(), any()))
                .thenThrow(new InvalidContentSourceException("Uploaded file is not a PDF"));

        mockMvc.perform(post("/contents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://www.youtube.com/watch?v=dQw4w9WgXcQ\"}")
                        .header("X-User-Id", USER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid content source"))
                .andExpect(jsonPath("$.detail").value("Uploaded file is not a PDF"));
    }

    @Test
    void listsBothContentTypesThroughOneResponseShape() throws Exception {
        when(catalog.list(eq(USER), any()))
                .thenReturn(new PageImpl<>(
                        List.of(pdfContent(), videoContent()), PageRequest.of(0, 20), 2));

        mockMvc.perform(get("/contents").header("X-User-Id", USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].type").value("PDF"))
                .andExpect(jsonPath("$.items[0].file_name").value("lecture.pdf"))
                .andExpect(jsonPath("$.items[1].type").value("VIDEO"))
                .andExpect(jsonPath("$.items[1].source_url").value("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
    }

    @Test
    void clampsAnAbsurdPageSize() throws Exception {
        when(catalog.list(eq(USER), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        mockMvc.perform(get("/contents").param("size", "10000").header("X-User-Id", USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void reportsAMissingContentAsProblemDetails() throws Exception {
        when(catalog.require(USER, CONTENT)).thenThrow(new ContentNotFoundException(CONTENT));

        mockMvc.perform(get("/contents/" + CONTENT).header("X-User-Id", USER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Content not found"));
    }

    @Test
    void returnsTheFullDetailShapeForASingleContent() throws Exception {
        when(catalog.require(USER, CONTENT)).thenReturn(pdfContent());

        mockMvc.perform(get("/contents/" + CONTENT).header("X-User-Id", USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content_id").value(CONTENT.toString()))
                .andExpect(jsonPath("$.type").value("PDF"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.file_name").value("lecture.pdf"))
                .andExpect(jsonPath("$.storage_path").value("raw/u/c/lecture.pdf"))
                .andExpect(jsonPath("$.created_at").exists())
                .andExpect(jsonPath("$.updated_at").exists());
    }

    /**
     * {@code URI} rejects this before ingestion ever sees it, so the controller has to turn a parse
     * failure into the same 400 that an unsupported host produces.
     */
    @Test
    void reportsAUrlThatIsNotEvenAUriAsABadRequest() throws Exception {
        mockMvc.perform(post("/contents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"h ttp://broken url\"}")
                        .header("X-User-Id", USER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Malformed URL: h ttp://broken url"));

        verify(ingestion, never()).ingest(any(), any());
    }

    @Test
    void rejectsTheRequestWhenTheUploadedPartCannotBeRead() throws Exception {
        MockMultipartFile unreadable = new MockMultipartFile(
                "file", "lecture.pdf", "application/pdf", new byte[] {1}) {
            @Override
            public byte[] getBytes() throws IOException {
                throw new IOException("stream closed");
            }
        };

        mockMvc.perform(multipart("/contents").file(unreadable).header("X-User-Id", USER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Could not read the uploaded file"));

        verify(ingestion, never()).ingest(any(), any());
    }

    @Test
    void acceptsDeletionForAsynchronousCleanup() throws Exception {
        mockMvc.perform(delete("/contents/" + CONTENT).header("X-User-Id", USER))
                .andExpect(status().isAccepted());

        verify(catalog).delete(USER, CONTENT);
    }

    private static Content pdfContent() {
        return Content.pdf(CONTENT, USER, "lecture.pdf", "raw/u/c/lecture.pdf", NOW);
    }

    private static Content videoContent() {
        return Content.video(CONTENT, USER, "https://www.youtube.com/watch?v=dQw4w9WgXcQ", NOW);
    }
}
