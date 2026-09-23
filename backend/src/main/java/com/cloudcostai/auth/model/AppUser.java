package com.cloudcostai.auth.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class AppUser {
    @Id private UUID id;
    @Column(nullable = false, unique = true) private String username;
    @Column(nullable = false, unique = true) private String email;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    protected AppUser() {}
    public AppUser(String username, String email, String passwordHash) {
        this.id = UUID.randomUUID(); this.username = username; this.email = email; this.passwordHash = passwordHash;
        this.createdAt = LocalDateTime.now(); this.updatedAt = createdAt;
    }
    public UUID getId() { return id; }
    public String getPasswordHash() { return passwordHash; }
    public AuthProfile profile() { return new AuthProfile(id, username, email); }
}