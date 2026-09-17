package com.contentservice.api;

import com.contentservice.api.dto.ContentAcceptedResponse;
import com.contentservice.api.dto.ContentDetailsResponse;
import com.contentservice.api.dto.ContentListResponse;
import com.contentservice.api.dto.ContentSummaryResponse;
import com.contentservice.api.dto.SubmitVideoRequest;
import com.contentservice.catalog.ContentCatalog;
import com.contentservice.domain.Content;
import com.contentservice.ingestion.ContentIngestion;
import com.contentservice.ingestion.ContentSource;
import com.contentservice.ingestion.InvalidContentSourceException;
import com.contentservice.web.CurrentUser;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * One resource for both content types.
 *
 * <p>{@code POST /contents} is selected by request media type: {@code multipart/form-data} carries a
 * PDF, {@code application/json} carries a YouTube URL. Two adapters, one seam — which is exactly
 * why this is one service and not the former document-service plus video-service.
 */
@RestController
@RequestMapping("/contents")
class ContentController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ContentIngestion ingestion;
    private final ContentCatalog catalog;

    ContentController(ContentIngestion ingestion, ContentCatalog catalog) {
        this.ingestion = ingestion;
        this.catalog = catalog;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ContentAcceptedResponse> uploadPdf(
            @CurrentUser UUID userId, @RequestPart("file") MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new InvalidContentSourceException("Could not read the uploaded file");
        }
        ContentSource source =
                new ContentSource.PdfUpload(file.getOriginalFilename(), bytes, file.getContentType());
        return accepted(ingestion.ingest(userId, source));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ContentAcceptedResponse> submitVideo(
            @CurrentUser UUID userId, @Valid @RequestBody SubmitVideoRequest request) {
        URI url;
        try {
            url = new URI(request.url());
        } catch (URISyntaxException ex) {
            throw new InvalidContentSourceException("Malformed URL: " + request.url());
        }
        return accepted(ingestion.ingest(userId, new ContentSource.YouTubeLink(url)));
    }

    @GetMapping
    ContentListResponse list(
            @CurrentUser UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<Content> found = catalog.list(userId, PageRequest.of(safePage, safeSize));
        List<ContentSummaryResponse> items =
                found.getContent().stream().map(ContentSummaryResponse::of).toList();
        return new ContentListResponse(items, safePage, safeSize, found.getTotalElements());
    }

    @GetMapping("/{contentId}")
    ContentDetailsResponse get(@CurrentUser UUID userId, @PathVariable UUID contentId) {
        return ContentDetailsResponse.of(catalog.require(userId, contentId));
    }

    @DeleteMapping("/{contentId}")
    ResponseEntity<Void> delete(@CurrentUser UUID userId, @PathVariable UUID contentId) {
        catalog.delete(userId, contentId);
        return ResponseEntity.accepted().build();
    }

    private static ResponseEntity<ContentAcceptedResponse> accepted(Content content) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ContentAcceptedResponse.of(content));
    }
}
