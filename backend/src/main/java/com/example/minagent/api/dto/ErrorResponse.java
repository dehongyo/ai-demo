package com.example.minagent.api.dto;

import java.time.Instant;

public record ErrorResponse(
        String code,
        String message,
        String requestId,
        Instant timestamp
) {
    public static ErrorResponse of(String code, String message, String requestId) {
        return new ErrorResponse(code, message, requestId, Instant.now());
    }
}
