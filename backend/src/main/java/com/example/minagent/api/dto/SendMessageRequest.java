package com.example.minagent.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotBlank @Size(min = 1, max = 8000) String content
) {
}
