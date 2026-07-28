package com.example.minagent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public record ToolDefinition(
        String name,
        String description,
        ObjectNode parametersSchema
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ToolDefinition of(String name, String description, ObjectNode parametersSchema) {
        return new ToolDefinition(name, description, parametersSchema);
    }
}
