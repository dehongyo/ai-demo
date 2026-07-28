package com.example.minagent.agent;

import java.util.List;

public record ParsedLlmOutput(
        OutputKind kind,
        String decisionSummary,
        List<AgentDecision.RequestedToolCall> toolCalls,
        String finalAnswer
) {
    public enum OutputKind {
        FINAL_ANSWER, TOOL_CALLS, INVALID
    }
}
