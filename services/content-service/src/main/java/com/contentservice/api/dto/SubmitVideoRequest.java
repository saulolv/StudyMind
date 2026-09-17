package com.contentservice.api.dto;

import jakarta.validation.constraints.NotBlank;

public record SubmitVideoRequest(@NotBlank String url) {
}
