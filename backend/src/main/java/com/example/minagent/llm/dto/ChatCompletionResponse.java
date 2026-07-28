package com.example.minagent.llm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatCompletionResponse(
        String id,
        List<Choice> choices,
        Usage usage
) {
    public record Choice(
            Integer index,
            AssistantMessage message,
            @JsonProperty("finish_reason") String finishReason
    ) {
    }

    public record AssistantMessage(
            String role,
            String content,
            @JsonProperty("reasoning_content") String reasoningContent,
            @JsonProperty("tool_calls") List<LlmToolCall> toolCalls
    ) {
    }

    public record Usage(
            @JsonProperty("prompt_tokens") int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens,
            @JsonProperty("total_tokens") int totalTokens
    ) {
    }
}
