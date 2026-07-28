package com.example.minagent.tool;

import java.util.UUID;

public record ToolContext(
        UUID userId,
        UUID sessionId,
        UUID runId
) {
}
