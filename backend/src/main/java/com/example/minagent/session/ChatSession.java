package com.example.minagent.session;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_session")
public class ChatSession {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, length = 200)
    private String title;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ChatSession() {
    }

    public ChatSession(UUID id, AppUser user, String title) {
        this.id = id;
        this.user = user;
        this.title = title;
    }

    public UUID getId() { return id; }
    public AppUser getUser() { return user; }
    public UUID getUserId() { return user.getId(); }
    public String getTitle() { return title; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setTitle(String title) {
        this.title = title;
    }

    @PreUpdate
    void touchUpdatedAt() {
        this.updatedAt = Instant.now();
    }
}
