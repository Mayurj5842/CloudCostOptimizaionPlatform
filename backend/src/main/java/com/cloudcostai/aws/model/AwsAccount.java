package com.cloudcostai.aws.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "aws_accounts")
public class AwsAccount {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "account_alias", nullable = false)
    private String accountAlias;

    @Column(nullable = false)
    private String region;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "iam_arn", nullable = false)
    private String iamArn;

    @Column(nullable = false)
    private String provider;

    @Column(name = "connection_status", nullable = false)
    private String connectionStatus;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected AwsAccount() {
    }

    public AwsAccount(String userId, String accountAlias, String region, String accountId,
                      String iamArn, String provider, String connectionStatus) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.accountAlias = accountAlias;
        this.region = region;
        this.accountId = accountId;
        this.iamArn = iamArn;
        this.provider = provider;
        this.connectionStatus = connectionStatus;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void markConnected(String accountAlias, String region, String accountId, String iamArn) {
        this.accountAlias = accountAlias;
        this.region = region;
        this.accountId = accountId;
        this.iamArn = iamArn;
        this.connectionStatus = "CONNECTED";
        this.updatedAt = LocalDateTime.now();
    }

    public void markDisconnected() {
        this.connectionStatus = "DISCONNECTED";
        this.updatedAt = LocalDateTime.now();
    }

    public String getUserId() { return userId; }
    public String getAccountAlias() { return accountAlias; }
    public String getRegion() { return region; }
    public String getAccountId() { return accountId; }
    public String getIamArn() { return iamArn; }
    public String getProvider() { return provider; }
    public String getConnectionStatus() { return connectionStatus; }
}