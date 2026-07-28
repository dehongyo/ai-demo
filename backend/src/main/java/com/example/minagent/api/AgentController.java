package com.example.minagent.api;

import com.example.minagent.agent.AgentRunCommand;
import com.example.minagent.agent.AgentRunResult;
import com.example.minagent.agent.AgentRuntime;
import com.example.minagent.api.dto.AgentRunResponse;
import com.example.minagent.api.dto.SendMessageRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/sessions/{sessionId}")
public class AgentController {

    private final AgentRuntime agentRuntime;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public AgentController(AgentRuntime agentRuntime) {
        this.agentRuntime = agentRuntime;
    }

    @PostMapping("/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public AgentRunResponse sendMessage(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID sessionId,
            @Valid @RequestBody SendMessageRequest request) {
        AgentRunResult result = agentRuntime.run(
                new AgentRunCommand(userId, sessionId, request.content()));
        return toResponse(result);
    }

    @PostMapping(value = "/runs:stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRun(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID sessionId,
            @Valid @RequestBody SendMessageRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);

        executor.submit(() -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("run_started")
                        .data("{\"sessionId\":\"" + sessionId + "\"}"));

                AgentRunResult result = agentRuntime.run(
                        new AgentRunCommand(userId, sessionId, request.content()));

                emitter.send(SseEmitter.event()
                        .name("answer_delta")
                        .data("{\"content\":\"" + escape(result.answer()) + "\"}"));

                emitter.send(SseEmitter.event()
                        .name("run_finished")
                        .data("{\"runId\":\"" + result.runId() + "\",\"status\":\"" + result.status() + "\"}"));

                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("{\"message\":\"" + escape(e.getMessage()) + "\"}"));
                    emitter.completeWithError(e);
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
            }
        });

        return emitter;
    }

    private AgentRunResponse toResponse(AgentRunResult result) {
        return new AgentRunResponse(result.runId(), result.messageId(),
                result.answer(), result.status());
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}
