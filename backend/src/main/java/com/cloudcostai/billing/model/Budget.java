package com.cloudcostai.billing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "budgets")
@SuppressWarnings("null")
public class Budget {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "aws_account_id", nullable = false)
    private String awsAccountId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private String period;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private String status;

    @Column(name = "alert_thresholds", nullable = false)
    private String alertThresholds;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Budget() {
    }

    public Budget(String userId, String awsAccountId, String name, BigDecimal amount, String currency,
                  String period, LocalDate startDate, LocalDate endDate, String status, List<Integer> alertThresholds) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.awsAccountId = awsAccountId;
        this.name = name;
        this.amount = amount;
        this.currency = currency;
        this.period = period;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
        this.alertThresholds = normalizeThresholds(alertThresholds);
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void update(String name, BigDecimal amount, String currency, String period,
                       LocalDate startDate, LocalDate endDate, String status, List<Integer> alertThresholds) {
        this.name = name;
        this.amount = amount;
        this.currency = currency;
        this.period = period;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
        this.alertThresholds = normalizeThresholds(alertThresholds);
        this.updatedAt = LocalDateTime.now();
    }

    public List<Integer> getAlertThresholdsList() {
        if (alertThresholds == null || alertThresholds.isBlank()) {
            return List.of(50, 80, 90, 100);
        }
        return Arrays.stream(alertThresholds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .sorted()
                .toList();
    }

    public String getAlertThresholds() {
        return alertThresholds;
    }

    public void setAlertThresholds(String alertThresholds) {
        this.alertThresholds = alertThresholds;
    }

    private static String normalizeThresholds(List<Integer> thresholds) {
        List<Integer> values = thresholds == null || thresholds.isEmpty() ? List.of(50, 80, 90, 100) : thresholds.stream().filter(v -> v != null && v > 0 && v <= 100).distinct().sorted().toList();
        return String.join(",", values.stream().map(String::valueOf).toList());
    }

    public UUID getId() { return id; }
    public String getUserId() { return userId; }
    public String getAwsAccountId() { return awsAccountId; }
    public String getName() { return name; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getPeriod() { return period; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; this.updatedAt = LocalDateTime.now(); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
