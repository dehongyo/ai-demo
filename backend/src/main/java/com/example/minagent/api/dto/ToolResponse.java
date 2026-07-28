package com.example.minagent.api.dto;

import com.example.minagent.tool.ToolDefinition;

public record ToolResponse(
        String name,
        String description,
        com.fasterxml.jackson.databind.node.ObjectNode parametersSchema
) {
    public static ToolResponse from(ToolDefinition def) {
        return new ToolResponse(def.name(), def.description(), def.parametersSchema());
    }
}
