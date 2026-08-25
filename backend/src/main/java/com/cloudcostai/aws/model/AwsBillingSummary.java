package com.cloudcostai.aws.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "aws_billing_summary", uniqueConstraints = @UniqueConstraint(columnNames = {
        "aws_account_id", "billing_period"}))
public class AwsBillingSummary {
    @Id
    private UUID id;

    @Column(name = "aws_account_id", nullable = false)
    private String awsAccountId;

    @Column(name = "billing_period", nullable = false)
    private String billingPeriod;

    @Column(name = "total_cost", nullable = false)
    private BigDecimal totalCost;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private boolean estimated;

    @Column(name = "last_synced_at", nullable = false)
    private LocalDateTime lastSyncedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected AwsBillingSummary() {
    }

    public AwsBillingSummary(String awsAccountId,
                             String billingPeriod,
                             BigDecimal totalCost,
                             String currency,
                             boolean estimated,
                             LocalDateTime lastSyncedAt) {
        this.id = UUID.randomUUID();
        this.awsAccountId = awsAccountId;
        this.billingPeriod = billingPeriod;
        this.totalCost = totalCost;
        this.currency = currency;
        this.estimated = estimated;
        this.lastSyncedAt = lastSyncedAt;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void update(BigDecimal totalCost, String currency, boolean estimated, LocalDateTime lastSyncedAt) {
        this.totalCost = totalCost;
        this.currency = currency;
        this.estimated = estimated;
        this.lastSyncedAt = lastSyncedAt;
        this.updatedAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getAwsAccountId() {
        return awsAccountId;
    }

    public String getBillingPeriod() {
        return billingPeriod;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public String getCurrency() {
        return currency;
    }

    public boolean isEstimated() {
        return estimated;
    }

    public LocalDateTime getLastSyncedAt() {
        return lastSyncedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
