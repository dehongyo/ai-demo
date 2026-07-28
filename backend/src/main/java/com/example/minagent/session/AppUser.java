package com.example.minagent.session;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    private UUID id;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected AppUser() {
    }

    public AppUser(UUID id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public UUID getId() { return id; }
    public String getDisplayName() { return displayName; }
    public Instant getCreatedAt() { return createdAt; }
}
