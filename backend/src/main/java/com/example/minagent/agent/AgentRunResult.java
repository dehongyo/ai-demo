package com.example.minagent.agent;

import java.util.UUID;

public record AgentRunResult(
        UUID runId,
        UUID messageId,
        String answer,
        String status
) {
    public static AgentRunResult completed(UUID runId, UUID messageId, String answer) {
        return new AgentRunResult(runId, messageId, answer, "COMPLETED");
    }

    public static AgentRunResult maxSteps(UUID runId, UUID messageId, String answer) {
        return new AgentRunResult(runId, messageId, answer, "MAX_STEPS");
    }

    public static AgentRunResult error(UUID runId, String errorMessage) {
        return new AgentRunResult(runId, null, errorMessage, "ERROR");
    }
}
