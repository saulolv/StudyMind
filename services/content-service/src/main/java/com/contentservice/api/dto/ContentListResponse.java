package com.contentservice.api.dto;

import java.util.List;

public record ContentListResponse(
        List<ContentSummaryResponse> items, int page, int size, long total) {
}
