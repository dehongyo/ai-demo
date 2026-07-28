package com.example.minagent.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LlmToolCall(
        String id,
        String type,
        LlmFunctionCall function
) {
    public record LlmFunctionCall(
            String name,
            String arguments
    ) {
    }
}
