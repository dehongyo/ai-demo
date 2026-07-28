package com.example.minagent.api.dto;

import com.example.minagent.session.ChatMessage;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        String role,
        String content,
        String toolName,
        String toolCallId,
        String toolCallsJson,
        long sequenceNo,
        Instant createdAt
) {
    public static MessageResponse from(ChatMessage message) {
        return new MessageResponse(
                message.getId(),
                message.getRole().name(),
                message.getContent(),
                message.getToolName(),
                message.getToolCallId(),
                message.getToolCallsJson(),
                message.getSequenceNo(),
                message.getCreatedAt()
        );
    }
}
