package com.cloudcostai.notifications.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "aws_account_id", nullable = false, length = 100)
    private String awsAccountId;

    @Column(name = "email_address", nullable = false, length = 320)
    private String emailAddress;

    @Column(name = "email_notifications_enabled", nullable = false)
    private boolean emailNotificationsEnabled;

    @Column(name = "budget_alert_enabled", nullable = false)
    private boolean budgetAlertEnabled;

    @Column(name = "budget_alert_threshold", nullable = false)
    private int budgetAlertThreshold;

    @Column(name = "budget_exceeded_enabled", nullable = false)
    private boolean budgetExceededEnabled;

    @Column(name = "cost_anomaly_enabled", nullable = false)
    private boolean costAnomalyEnabled;

    @Column(name = "monthly_summary_enabled", nullable = false)
    private boolean monthlySummaryEnabled;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected NotificationPreference() {}

    public NotificationPreference(UUID userId, String awsAccountId, String emailAddress) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.awsAccountId = awsAccountId;
        this.emailAddress = emailAddress;
        this.emailNotificationsEnabled = false;
        this.budgetAlertEnabled = true;
        this.budgetAlertThreshold = 80;
        this.budgetExceededEnabled = true;
        this.costAnomalyEnabled = true;
        this.monthlySummaryEnabled = true;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = createdAt;
    }

    public void update(String emailAddress, boolean emailNotificationsEnabled, boolean budgetAlertEnabled,
                       int budgetAlertThreshold, boolean budgetExceededEnabled, boolean costAnomalyEnabled,
                       boolean monthlySummaryEnabled) {
        this.emailAddress = emailAddress;
        this.emailNotificationsEnabled = emailNotificationsEnabled;
        this.budgetAlertEnabled = budgetAlertEnabled;
        this.budgetAlertThreshold = budgetAlertThreshold;
        this.budgetExceededEnabled = budgetExceededEnabled;
        this.costAnomalyEnabled = costAnomalyEnabled;
        this.monthlySummaryEnabled = monthlySummaryEnabled;
        this.updatedAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getAwsAccountId() { return awsAccountId; }
    public String getEmailAddress() { return emailAddress; }
    public boolean isEmailNotificationsEnabled() { return emailNotificationsEnabled; }
    public boolean isBudgetAlertEnabled() { return budgetAlertEnabled; }
    public int getBudgetAlertThreshold() { return budgetAlertThreshold; }
    public boolean isBudgetExceededEnabled() { return budgetExceededEnabled; }
    public boolean isCostAnomalyEnabled() { return costAnomalyEnabled; }
    public boolean isMonthlySummaryEnabled() { return monthlySummaryEnabled; }
}