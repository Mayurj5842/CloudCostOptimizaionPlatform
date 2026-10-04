package com.cloudcostai.aws.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "aws_recommendations")
public class AwsRecommendation {
    @Id
    private UUID id;

    @Column(name = "aws_account_id", nullable = false)
    private String awsAccountId;

    @Column(name = "recommendation_key", nullable = false, unique = true)
    private String recommendationKey;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String severity;

    @Column(nullable = false)
    private String service;

    @Column(name = "resource_id")
    private String resourceId;

    @Column
    private String region;

    @Column(name = "current_cost")
    private BigDecimal currentCost;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String recommendation;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String action;

    @Column(name = "potential_savings")
    private BigDecimal potentialSavings;

    @Column(nullable = false)
    private String status;

    @Column(name = "billing_month")
    private String billingMonth;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected AwsRecommendation() {
    }

    public AwsRecommendation(String awsAccountId, String recommendationKey, String title, String category,
                             String severity, String service, String resourceId, String region,
                             BigDecimal currentCost, String reason, String recommendation, String action,
                             BigDecimal potentialSavings, String status) {
                    this(awsAccountId, recommendationKey, title, category, severity, service, resourceId, region, currentCost,
                        reason, recommendation, action, potentialSavings, status, null);
                    }

                    public AwsRecommendation(String awsAccountId, String recommendationKey, String title, String category,
                                 String severity, String service, String resourceId, String region,
                                 BigDecimal currentCost, String reason, String recommendation, String action,
                                 BigDecimal potentialSavings, String status, String billingMonth) {
        this.id = UUID.randomUUID();
        this.awsAccountId = awsAccountId;
        this.recommendationKey = recommendationKey;
        this.createdAt = LocalDateTime.now();
        update(title, category, severity, service, resourceId, region, currentCost, reason, recommendation,
                        action, potentialSavings, status, billingMonth);
    }

    public void update(String title, String category, String severity, String service, String resourceId,
                       String region, BigDecimal currentCost, String reason, String recommendation,
                       String action, BigDecimal potentialSavings, String status) {
                update(title, category, severity, service, resourceId, region, currentCost, reason, recommendation,
                    action, potentialSavings, status, this.billingMonth);
                }

                public void update(String title, String category, String severity, String service, String resourceId,
                           String region, BigDecimal currentCost, String reason, String recommendation,
                           String action, BigDecimal potentialSavings, String status, String billingMonth) {
        this.title = title;
        this.category = category;
        this.severity = severity;
        this.service = service;
        this.resourceId = resourceId;
        this.region = region;
        this.currentCost = currentCost;
        this.reason = reason;
        this.recommendation = recommendation;
        this.action = action;
        this.potentialSavings = potentialSavings;
        this.status = status;
        this.billingMonth = billingMonth;
    }

    public UUID getId() { return id; }
    public String getAwsAccountId() { return awsAccountId; }
    public String getRecommendationKey() { return recommendationKey; }
    public String getTitle() { return title; }
    public String getCategory() { return category; }
    public String getSeverity() { return severity; }
    public String getService() { return service; }
    public String getResourceId() { return resourceId; }
    public String getRegion() { return region; }
    public BigDecimal getCurrentCost() { return currentCost; }
    public String getReason() { return reason; }
    public String getRecommendation() { return recommendation; }
    public String getAction() { return action; }
    public BigDecimal getPotentialSavings() { return potentialSavings; }
    public String getStatus() { return status; }
    public String getBillingMonth() { return billingMonth; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}