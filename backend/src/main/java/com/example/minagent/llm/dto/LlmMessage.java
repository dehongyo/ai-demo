package com.example.minagent.llm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LlmMessage(
        String role,
        String content,
        List<LlmToolCall> tool_calls,
        @JsonProperty("tool_call_id") String toolCallId,
        String name
) {
    public static LlmMessage system(String content) {
        return new LlmMessage("system", content, null, null, null);
    }

    public static LlmMessage user(String content) {
        return new LlmMessage("user", content, null, null, null);
    }

    public static LlmMessage assistant(String content) {
        return new LlmMessage("assistant", content, null, null, null);
    }

    public static LlmMessage assistantToolCalls(List<LlmToolCall> toolCalls) {
        return new LlmMessage("assistant", null, toolCalls, null, null);
    }

    public static LlmMessage tool(String toolCallId, String toolName, String content) {
        return new LlmMessage("tool", content, null, toolCallId, toolName);
    }
}
