package com.example.minagent.agent;

import java.util.UUID;

public record AgentRunCommand(
        UUID userId,
        UUID sessionId,
        String content
) {
}
