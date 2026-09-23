package com.cloudcostai.billing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "budget_alerts")
public class BudgetAlert {
    @Id
    private UUID id;

    @Column(name = "budget_id", nullable = false)
    private UUID budgetId;

    @Column(name = "threshold_percent", nullable = false)
    private Integer thresholdPercent;

    @Column(name = "alert_period", nullable = false)
    private String alertPeriod;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "triggered_at", nullable = false)
    private LocalDateTime triggeredAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    protected BudgetAlert() {
    }

    public BudgetAlert(UUID budgetId, Integer thresholdPercent, String alertPeriod, String status, String message) {
        this.id = UUID.randomUUID();
        this.budgetId = budgetId;
        this.thresholdPercent = thresholdPercent;
        this.alertPeriod = alertPeriod;
        this.status = status;
        this.message = message;
        this.triggeredAt = LocalDateTime.now();
    }

    public void markRead() {
        this.readAt = LocalDateTime.now();
        this.status = "READ";
    }

    public UUID getId() { return id; }
    public UUID getBudgetId() { return budgetId; }
    public Integer getThresholdPercent() { return thresholdPercent; }
    public String getAlertPeriod() { return alertPeriod; }
    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public LocalDateTime getTriggeredAt() { return triggeredAt; }
    public LocalDateTime getReadAt() { return readAt; }
}
