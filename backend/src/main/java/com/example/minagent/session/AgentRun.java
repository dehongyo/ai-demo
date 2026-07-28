package com.example.minagent.session;

import java.time.Instant;
import java.util.UUID;

public record AgentRun(
        UUID id,
        UUID userId,
        UUID sessionId,
        UUID triggerMessageId,
        RunStatus status,
        String finalAnswer,
        int totalPromptTokens,
        int totalCompletionTokens,
        Instant startedAt,
        Instant finishedAt,
        String errorCode,
        String errorMessage
) {
    public enum RunStatus {
        RUNNING, COMPLETED, MAX_STEPS, ERROR, CANCELLED
    }
}
