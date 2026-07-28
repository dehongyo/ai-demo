package com.example.minagent.api.dto;

import com.example.minagent.session.ChatSession;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        String title,
        Instant createdAt,
        Instant updatedAt
) {
    public static SessionResponse from(ChatSession session) {
        return new SessionResponse(
                session.getId(),
                session.getTitle(),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }
}
