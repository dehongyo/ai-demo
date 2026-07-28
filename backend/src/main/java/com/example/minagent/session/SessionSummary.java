package com.example.minagent.session;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "session_summary")
public class SessionSummary {

    @Id
    @Column(name = "session_id")
    private UUID sessionId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "session_id")
    private ChatSession session;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "covered_until_message_id", nullable = false)
    private ChatMessage coveredUntilMessage;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected SessionSummary() {
    }

    public SessionSummary(ChatSession session, String content, ChatMessage coveredUntilMessage) {
        this.session = session;
        this.content = content;
        this.coveredUntilMessage = coveredUntilMessage;
    }

    public UUID getSessionId() { return sessionId; }
    public String getContent() { return content; }
    public ChatMessage getCoveredUntilMessage() { return coveredUntilMessage; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setContent(String content) { this.content = content; }
    public void setCoveredUntilMessage(ChatMessage coveredUntilMessage) { this.coveredUntilMessage = coveredUntilMessage; }

    @PreUpdate
    void touchUpdatedAt() {
        this.updatedAt = Instant.now();
    }
}
