package com.cloudcostai.auth.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "app_sessions")
public class AppSession {
    @Id @Column(length = 64) private String token;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private AppUser user;
    @Column(name = "expires_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE") private Instant expiresAt;
    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE") private Instant createdAt;
    protected AppSession() {}
    public AppSession(String token, AppUser user, Instant expiresAt) {
        this.token = token;
        this.user = user;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }
    @PrePersist
    private void populateCreatedAt() {
        if (createdAt == null) createdAt = Instant.now();
    }
    public AppUser getUser() { return user; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
}