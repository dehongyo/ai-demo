package com.example.minagent.session;

import com.example.minagent.session.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class SessionService {

    private final AppUserRepository appUserRepository;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final SessionSummaryRepository summaryRepository;

    public SessionService(AppUserRepository appUserRepository,
                          ChatSessionRepository sessionRepository,
                          ChatMessageRepository messageRepository,
                          SessionSummaryRepository summaryRepository) {
        this.appUserRepository = appUserRepository;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.summaryRepository = summaryRepository;
    }

    public AppUser findOrCreateUser(UUID userId) {
        return appUserRepository.findById(userId)
                .orElseGet(() -> appUserRepository.save(new AppUser(userId, "User-" + userId.toString().substring(0, 8))));
    }

    public ChatSession createSession(UUID userId, String title) {
        AppUser user = findOrCreateUser(userId);
        ChatSession session = new ChatSession(UUID.randomUUID(), user, title);
        return sessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public List<ChatSession> listSessions(UUID userId) {
        return sessionRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public ChatSession getSession(UUID userId, UUID sessionId) {
        return requireOwnedSession(userId, sessionId);
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getMessages(UUID userId, UUID sessionId) {
        requireOwnedSession(userId, sessionId);
        return messageRepository.findBySessionIdOrderBySequenceNoAsc(sessionId);
    }

    @Transactional(readOnly = true)
    public Optional<SessionSummary> getSummary(UUID sessionId) {
        return summaryRepository.findBySessionId(sessionId);
    }

    @Transactional(readOnly = true)
    public ChatSession requireOwnedSession(UUID userId, UUID sessionId) {
        ChatSession session = sessionRepository.findByIdWithUser(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(
                        "Session " + sessionId + " not found"));
        if (!session.getUserId().equals(userId)) {
            throw new SessionNotFoundException(
                    "Session " + sessionId + " not found");
        }
        return session;
    }

    public ChatMessage saveMessage(ChatMessage message) {
        return messageRepository.save(message);
    }

    public long nextSequenceNo(UUID sessionId) {
        return messageRepository.maxSequenceNoBySessionId(sessionId) + 1;
    }

    public long countMessages(UUID sessionId) {
        return messageRepository.countBySessionId(sessionId);
    }

    public void saveSummary(SessionSummary summary) {
        summaryRepository.save(summary);
    }

    public List<ChatMessage> getRecentMessages(UUID sessionId, int count) {
        return messageRepository.findRecentBySessionId(sessionId, count);
    }

    public ChatSession saveSession(ChatSession session) {
        return sessionRepository.save(session);
    }
}
