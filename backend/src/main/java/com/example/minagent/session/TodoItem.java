package com.example.minagent.session;

import java.time.Instant;
import java.util.UUID;

public record TodoItem(
        UUID id,
        UUID userId,
        UUID sessionId,
        String content,
        String status,
        Instant createdAt,
        Instant completedAt
) {
    public boolean isOpen() {
        return "OPEN".equals(status);
    }

    public TodoItem complete() {
        return new TodoItem(id, userId, sessionId, content, "COMPLETED", createdAt, Instant.now());
    }
}
