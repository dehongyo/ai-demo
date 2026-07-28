package com.example.minagent.api.dto;

import java.util.UUID;

public record AgentRunResponse(
        UUID runId,
        UUID messageId,
        String answer,
        String status
) {
}
