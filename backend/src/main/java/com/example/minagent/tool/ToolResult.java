package com.example.minagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;

public record ToolResult(
        boolean success,
        String code,
        JsonNode data,
        String modelMessage
) {
    public static ToolResult success(JsonNode data, String modelMessage) {
        return new ToolResult(true, "OK", data, modelMessage);
    }

    public static ToolResult failure(String code, String modelMessage) {
        return new ToolResult(false, code, NullNode.getInstance(), modelMessage);
    }

    public String toModelJson() {
        return modelMessage;
    }
}
