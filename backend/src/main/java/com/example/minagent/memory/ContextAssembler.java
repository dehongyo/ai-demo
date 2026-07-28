package com.example.minagent.memory;

import com.example.minagent.llm.dto.LlmMessage;
import com.example.minagent.llm.dto.LlmToolCall;
import com.example.minagent.session.ChatMessage;
import com.example.minagent.session.SessionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class ContextAssembler {

    private static final Logger log = LoggerFactory.getLogger(ContextAssembler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SessionService sessionService;

    public ContextAssembler(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    public List<LlmMessage> build(UUID sessionId, int recentCount, String systemPrompt) {
        List<LlmMessage> messages = new ArrayList<>();

        // 1. System prompt
        messages.add(LlmMessage.system(systemPrompt));

        // 2. Session summary
        sessionService.getSummary(sessionId).ifPresent(summary ->
                messages.add(LlmMessage.system("会话历史摘要:\n" + summary.getContent())));

        // 3. Recent messages (chronological order)
        List<ChatMessage> recent = sessionService.getRecentMessages(sessionId, recentCount);
        for (int i = recent.size() - 1; i >= 0; i--) {
            messages.add(toLlmMessage(recent.get(i)));
        }

        return messages;
    }

    private LlmMessage toLlmMessage(ChatMessage msg) {
        return switch (msg.getRole()) {
            case USER -> LlmMessage.user(msg.getContent());
            case ASSISTANT -> LlmMessage.assistant(msg.getContent());
            case ASSISTANT_TOOL_CALL -> deserializeToolCalls(msg);
            case TOOL -> LlmMessage.tool(
                    msg.getToolCallId(), msg.getToolName(), msg.getContent());
        };
    }

    private LlmMessage deserializeToolCalls(ChatMessage msg) {
        try {
            List<LlmToolCall> calls = MAPPER.readValue(
                    msg.getToolCallsJson(),
                    new TypeReference<List<LlmToolCall>>() {});
            return LlmMessage.assistantToolCalls(calls);
        } catch (Exception e) {
            log.warn("Failed to deserialize tool calls for message {}", msg.getId(), e);
            return LlmMessage.assistant(msg.getContent());
        }
    }
}
