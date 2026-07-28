package com.example.minagent.agent;

import com.example.minagent.llm.dto.ChatCompletionResponse;
import com.example.minagent.llm.dto.LlmToolCall;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class LlmOutputParser {

    private static final Logger log = LoggerFactory.getLogger(LlmOutputParser.class);
    private static final int MAX_EXCERPT_LENGTH = 200;

    private final ObjectMapper mapper = new ObjectMapper();

    public AgentDecision parse(ChatCompletionResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            return new AgentDecision.InvalidDecision("Empty response or choices", "");
        }

        var message = response.choices().getFirst().message();
        if (message == null) {
            return new AgentDecision.InvalidDecision("Null assistant message", "");
        }

        List<LlmToolCall> toolCalls = message.toolCalls();
        boolean hasToolCalls = toolCalls != null && !toolCalls.isEmpty();
        boolean hasContent = message.content() != null && !message.content().isBlank();

        if (hasToolCalls) {
            return parseToolCalls(toolCalls, message.content());
        } else if (hasContent) {
            return new AgentDecision.FinalAnswerDecision(message.content().trim());
        } else {
            String excerpt = truncate("content and tool_calls both empty");
            return new AgentDecision.InvalidDecision(
                    "Response has no content and no tool_calls", excerpt);
        }
    }

    private AgentDecision parseToolCalls(List<LlmToolCall> toolCalls, String reasoningContent) {
        List<AgentDecision.RequestedToolCall> calls = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (LlmToolCall tc : toolCalls) {
            try {
                String toolName = tc.function() != null ? tc.function().name() : null;
                String argsStr = tc.function() != null ? tc.function().arguments() : "{}";

                if (toolName == null || toolName.isBlank()) {
                    errors.add("Tool call missing function name");
                    continue;
                }

                JsonNode args;
                try {
                    args = mapper.readTree(argsStr);
                } catch (JsonProcessingException e) {
                    errors.add("Invalid arguments JSON for tool " + toolName + ": " + e.getMessage());
                    continue;
                }

                calls.add(new AgentDecision.RequestedToolCall(
                        tc.id(), toolName, args));
            } catch (Exception e) {
                log.warn("Failed to parse tool call: {}", e.getMessage());
                errors.add("Failed to parse tool call: " + e.getMessage());
            }
        }

        String summary;
        if (reasoningContent != null && !reasoningContent.isBlank()) {
            summary = reasoningContent.trim();
        } else {
            summary = "Model selected tool(s): " +
                    calls.stream().map(AgentDecision.RequestedToolCall::toolName)
                            .reduce((a, b) -> a + ", " + b).orElse("unknown");
        }

        return new AgentDecision.ToolCallsDecision(summary, List.copyOf(calls));
    }

    private String truncate(String text) {
        if (text == null) return "";
        return text.length() > MAX_EXCERPT_LENGTH
                ? text.substring(0, MAX_EXCERPT_LENGTH) + "..."
                : text;
    }
}
