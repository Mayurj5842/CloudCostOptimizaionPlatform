package com.cloudcostai.aws.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "aws_connection_sessions")
public class AwsConnectionSession {

    @Id
    @Column(length = 64)
    private String token;

    @Column(name = "account_id", nullable = false, length = 128)
    private String accountId;

    @Column(name = "access_key", nullable = false, length = 512)
    private String accessKey;

    @Column(name = "secret_key", nullable = false, length = 512)
    private String secretKey;

    @Column(name = "region", nullable = false, length = 100)
    private String region;

    @Column(name = "expires_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant createdAt;

    protected AwsConnectionSession() {
    }

    public AwsConnectionSession(String token, String accountId, String accessKey, String secretKey,
                               String region, Instant expiresAt) {
        this.token = token;
        this.accountId = accountId;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.region = region;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public String getToken() {
        return token;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public String getRegion() {
        return region;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
