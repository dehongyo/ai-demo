package com.example.minagent.llm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatCompletionRequest(
        String model,
        List<LlmMessage> messages,
        List<LlmToolDefinition> tools,
        @JsonProperty("tool_choice") String toolChoice,
        Double temperature,
        @JsonProperty("enable_thinking") Boolean enableThinking
) {
    public static ChatCompletionRequest of(String model, List<LlmMessage> messages,
                                           List<LlmToolDefinition> tools) {
        return new ChatCompletionRequest(model, messages, tools, "auto", 0.2, false);
    }
}
