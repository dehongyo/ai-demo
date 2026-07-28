package com.example.minagent.agent;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public sealed interface AgentDecision
        permits AgentDecision.FinalAnswerDecision,
                AgentDecision.ToolCallsDecision,
                AgentDecision.InvalidDecision {

    record FinalAnswerDecision(String answer) implements AgentDecision {
    }

    record ToolCallsDecision(
            String reasoningSummary,
            List<RequestedToolCall> calls
    ) implements AgentDecision {
    }

    record InvalidDecision(String reason, String rawResponseExcerpt)
            implements AgentDecision {
    }

    record RequestedToolCall(
            String callId,
            String toolName,
            JsonNode arguments
    ) {
    }
}
