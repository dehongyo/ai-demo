package com.example.minagent.tool;

import com.example.minagent.llm.dto.LlmToolDefinition;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ToolRegistry {

    private final Map<String, AgentTool> tools;

    public ToolRegistry(List<AgentTool> discoveredTools) {
        Map<String, AgentTool> index = new LinkedHashMap<>();
        for (AgentTool tool : discoveredTools) {
            String name = tool.definition().name();
            if (index.putIfAbsent(name, tool) != null) {
                throw new IllegalStateException("Duplicate tool name: " + name);
            }
        }
        this.tools = Map.copyOf(index);
    }

    public List<LlmToolDefinition> definitions() {
        return tools.values().stream()
                .map(t -> new LlmToolDefinition("function",
                        new LlmToolDefinition.FunctionDefinition(
                                t.definition().name(),
                                t.definition().description(),
                                t.definition().parametersSchema())))
                .toList();
    }

    public AgentTool require(String name) {
        AgentTool tool = tools.get(name);
        if (tool == null) {
            throw new UnknownToolException(name);
        }
        return tool;
    }

    public Set<String> getToolNames() {
        return tools.keySet();
    }

    public List<ToolDefinition> toolDefinitions() {
        return tools.values().stream().map(AgentTool::definition).toList();
    }
}
