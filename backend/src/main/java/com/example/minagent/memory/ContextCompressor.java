package com.example.minagent.memory;

import com.example.minagent.config.AgentProperties;
import com.example.minagent.session.*;
import com.example.minagent.session.repository.ChatMessageRepository;
import com.example.minagent.session.repository.SessionSummaryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class ContextCompressor {

    private static final Logger log = LoggerFactory.getLogger(ContextCompressor.class);

    private final SessionService sessionService;
    private final AgentProperties properties;
    private final ChatMessageRepository messageRepository;
    private final SessionSummaryRepository summaryRepository;

    public ContextCompressor(SessionService sessionService,
                             AgentProperties properties,
                             ChatMessageRepository messageRepository,
                             SessionSummaryRepository summaryRepository) {
        this.sessionService = sessionService;
        this.properties = properties;
        this.messageRepository = messageRepository;
        this.summaryRepository = summaryRepository;
    }

    public void evaluate(UUID sessionId) {
        try {
            long messageCount = sessionService.countMessages(sessionId);
            int totalTokens = estimateContextTokens(sessionId);

            if (messageCount <= properties.getCompressMessageThreshold()
                    && totalTokens <= properties.getContextTokenBudget()) {
                return;
            }

            log.info("Compressing session {}: {} messages, ~{} tokens",
                    sessionId, messageCount, totalTokens);

            // Build simple summary from recent messages
            List<ChatMessage> recentMsgs = sessionService.getRecentMessages(
                    sessionId, properties.getRecentMessageCount());
            if (recentMsgs.isEmpty()) return;

            StringBuilder sb = new StringBuilder();
            summaryRepository.findBySessionId(sessionId)
                    .ifPresent(s -> sb.append(s.getContent()).append("\n"));

            for (int i = recentMsgs.size() - 1; i >= 0; i--) {
                ChatMessage msg = recentMsgs.get(i);
                sb.append("[").append(msg.getRole()).append("] ").append(msg.getContent()).append("\n");
            }

            log.info("Session {} compression summary built: {} chars",
                    sessionId, sb.length());
        } catch (Exception e) {
            log.warn("Session {} compression failed (non-fatal): {}", sessionId, e.getMessage());
        }
    }

    private int estimateContextTokens(UUID sessionId) {
        List<ChatMessage> messages = sessionService.getRecentMessages(
                sessionId, properties.getRecentMessageCount());
        StringBuilder text = new StringBuilder();
        for (ChatMessage msg : messages) {
            text.append(msg.getContent()).append("\n");
        }
        return TokenEstimator.estimate(text.toString());
    }
}
