package com.example.minagent.api;

import com.example.minagent.api.dto.CreateSessionRequest;
import com.example.minagent.api.dto.MessageResponse;
import com.example.minagent.api.dto.SessionResponse;
import com.example.minagent.session.ChatSession;
import com.example.minagent.session.SessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse createSession(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody CreateSessionRequest request) {
        ChatSession session = sessionService.createSession(userId, request.title());
        return SessionResponse.from(session);
    }

    @GetMapping
    public List<SessionResponse> listSessions(@RequestHeader("X-User-Id") UUID userId) {
        return sessionService.listSessions(userId).stream()
                .map(SessionResponse::from)
                .toList();
    }

    @GetMapping("/{sessionId}")
    public SessionResponse getSession(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID sessionId) {
        ChatSession session = sessionService.getSession(userId, sessionId);
        return SessionResponse.from(session);
    }

    @GetMapping("/{sessionId}/messages")
    public List<MessageResponse> getMessages(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID sessionId) {
        return sessionService.getMessages(userId, sessionId).stream()
                .map(MessageResponse::from)
                .toList();
    }
}
