package com.example.minagent.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSessionRequest(
        @NotBlank @Size(min = 1, max = 200) String title
) {
}
