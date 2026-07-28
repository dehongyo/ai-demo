package com.example.minagent.session;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_message")
public class ChatMessage {

    public enum Role {
        USER, ASSISTANT, ASSISTANT_TOOL_CALL, TOOL
    }

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "tool_call_id", length = 200)
    private String toolCallId;

    @Column(name = "tool_name", length = 100)
    private String toolName;

    @Column(name = "tool_calls_json", columnDefinition = "text")
    private String toolCallsJson;

    @Column(name = "sequence_no", nullable = false)
    private long sequenceNo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ChatMessage() {
    }

    public ChatMessage(UUID id, ChatSession session, Role role, String content, long sequenceNo) {
        this.id = id;
        this.session = session;
        this.role = role;
        this.content = content;
        this.sequenceNo = sequenceNo;
    }

    public UUID getId() { return id; }
    public ChatSession getSession() { return session; }
    public UUID getSessionId() { return session.getId(); }
    public Role getRole() { return role; }
    public String getContent() { return content; }
    public String getToolCallId() { return toolCallId; }
    public String getToolName() { return toolName; }
    public String getToolCallsJson() { return toolCallsJson; }
    public long getSequenceNo() { return sequenceNo; }
    public Instant getCreatedAt() { return createdAt; }

    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public void setToolCallsJson(String toolCallsJson) { this.toolCallsJson = toolCallsJson; }

    public static ChatMessage user(UUID id, ChatSession session, String content, long sequenceNo) {
        return new ChatMessage(id, session, Role.USER, content, sequenceNo);
    }

    public static ChatMessage assistant(UUID id, ChatSession session, String content, long sequenceNo) {
        return new ChatMessage(id, session, Role.ASSISTANT, content, sequenceNo);
    }

    public static ChatMessage assistantToolCall(UUID id, ChatSession session, String toolCallsJson, long sequenceNo) {
        ChatMessage msg = new ChatMessage(id, session, Role.ASSISTANT_TOOL_CALL, "", sequenceNo);
        msg.setToolCallsJson(toolCallsJson);
        return msg;
    }

    public static ChatMessage tool(UUID id, ChatSession session, String toolCallId, String toolName, String content, long sequenceNo) {
        ChatMessage msg = new ChatMessage(id, session, Role.TOOL, content, sequenceNo);
        msg.setToolCallId(toolCallId);
        msg.setToolName(toolName);
        return msg;
    }
}
