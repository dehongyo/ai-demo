package com.example.minagent.llm.dto;

import com.fasterxml.jackson.databind.node.ObjectNode;

public record LlmToolDefinition(
        String type,
        FunctionDefinition function
) {
    public LlmToolDefinition {
        if (type == null) type = "function";
    }

    public record FunctionDefinition(
            String name,
            String description,
            ObjectNode parameters
    ) {
    }
}
