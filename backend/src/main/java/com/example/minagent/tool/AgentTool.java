package com.example.minagent.tool;

import com.fasterxml.jackson.databind.JsonNode;

public interface AgentTool {

    ToolDefinition definition();

    ToolResult execute(JsonNode arguments, ToolContext context);
}
