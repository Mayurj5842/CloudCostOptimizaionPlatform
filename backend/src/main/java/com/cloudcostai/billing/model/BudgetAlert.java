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

    @Column(name = "budget_id")
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

    @Column(name = "alert_type", nullable = false, length = 40)
    private String alertType;

    @Column(nullable = false, length = 20)
    private String severity;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "budget_name", length = 255)
    private String budgetName;

    @Column(name = "aws_account_id", length = 100)
    private String awsAccountId;

    @Column(name = "service_name", length = 255)
    private String serviceName;

    @Column(name = "actual_spend", precision = 19, scale = 4)
    private java.math.BigDecimal actualSpend;

    @Column(name = "budget_amount", precision = 19, scale = 4)
    private java.math.BigDecimal budgetAmount;

    @Column(length = 10)
    private String currency;

    @Column(name = "comparison_period", length = 50)
    private String comparisonPeriod;

    @Column(name = "percentage_change", precision = 9, scale = 2)
    private java.math.BigDecimal percentageChange;

    @Column(name = "event_key", unique = true, length = 300)
    private String eventKey;

    protected BudgetAlert() {
    }

    public BudgetAlert(UUID budgetId, Integer thresholdPercent, String alertPeriod, String status, String message) {
        this(budgetId, thresholdPercent, alertPeriod, status, message, "BUDGET_THRESHOLD", "WARNING",
            "Budget threshold reached", null, null, null, null, null, null, null, null,
            budgetId + ":" + thresholdPercent + ":" + alertPeriod);
        }

        public BudgetAlert(UUID budgetId, Integer thresholdPercent, String alertPeriod, String status, String message,
                   String alertType, String severity, String title, String budgetName, String awsAccountId,
                   String serviceName, java.math.BigDecimal actualSpend, java.math.BigDecimal budgetAmount,
                   String currency, String comparisonPeriod, java.math.BigDecimal percentageChange,
                   String eventKey) {
        this.id = UUID.randomUUID();
        this.budgetId = budgetId;
        this.thresholdPercent = thresholdPercent;
        this.alertPeriod = alertPeriod;
        this.status = status;
        this.message = message;
        this.triggeredAt = LocalDateTime.now();
        this.alertType = alertType;
        this.severity = severity;
        this.title = title;
        this.budgetName = budgetName;
        this.awsAccountId = awsAccountId;
        this.serviceName = serviceName;
        this.actualSpend = actualSpend;
        this.budgetAmount = budgetAmount;
        this.currency = currency;
        this.comparisonPeriod = comparisonPeriod;
        this.percentageChange = percentageChange;
        this.eventKey = eventKey;
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
    public String getAlertType() { return alertType; }
    public String getSeverity() { return severity; }
    public String getTitle() { return title; }
    public String getBudgetName() { return budgetName; }
    public String getAwsAccountId() { return awsAccountId; }
    public String getServiceName() { return serviceName; }
    public java.math.BigDecimal getActualSpend() { return actualSpend; }
    public java.math.BigDecimal getBudgetAmount() { return budgetAmount; }
    public String getCurrency() { return currency; }
    public String getComparisonPeriod() { return comparisonPeriod; }
    public java.math.BigDecimal getPercentageChange() { return percentageChange; }
    public String getEventKey() { return eventKey; }
}
