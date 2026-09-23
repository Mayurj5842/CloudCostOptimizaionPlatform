package com.cloudcostai.aws.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "aws_billing")
public class AwsBilling {
    @Id
    private UUID id;

    @Column(name = "aws_account_id", nullable = false, length = 100)
    private String awsAccountId;

    @Column(name = "billing_period_start", nullable = false)
    private LocalDate billingPeriodStart;

    @Column(name = "billing_period_end", nullable = false)
    private LocalDate billingPeriodEnd;

    @Column(name = "service_name", nullable = false, length = 255)
    private String serviceName;

    @Column(name = "region", length = 100)
    private String region;

    @Column(name = "product_name", length = 255)
    private String productName;

    @Column(name = "usage_type", length = 255)
    private String usageType;

    @Column(name = "resource_id", length = 500)
    private String resourceId;

    @Column(name = "line_item_type", length = 100)
    private String lineItemType;

    @Column(name = "usage_start_date")
    private LocalDate usageStartDate;

    @Column(name = "usage_end_date")
    private LocalDate usageEndDate;

    @Column(name = "record_key", nullable = false, unique = true, length = 128)
    private String recordKey;

    @Column(name = "usage_amount", nullable = false)
    private BigDecimal usageAmount;

    @Column(name = "cost_amount", nullable = false)
    private BigDecimal costAmount;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(nullable = false)
    private boolean estimated;

    @Column(name = "data_source", nullable = false, length = 32)
    private String dataSource;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected AwsBilling() {
    }

    public AwsBilling(String awsAccountId,
                      LocalDate billingPeriodStart,
                      LocalDate billingPeriodEnd,
                      String serviceName,
                      String region,
                      BigDecimal usageAmount,
                      BigDecimal costAmount,
                      String currency,
                      boolean estimated,
                      String productName,
                      String usageType,
                      String resourceId,
                      String lineItemType,
                      LocalDate usageStartDate,
                      LocalDate usageEndDate,
                      String recordKey) {
                this(awsAccountId, billingPeriodStart, billingPeriodEnd, serviceName, region, usageAmount, costAmount,
                    currency, estimated, productName, usageType, resourceId, lineItemType, usageStartDate, usageEndDate,
                    recordKey, "CUR_2_0");
                }

                public AwsBilling(String awsAccountId,
                          LocalDate billingPeriodStart,
                          LocalDate billingPeriodEnd,
                          String serviceName,
                          String region,
                          BigDecimal usageAmount,
                          BigDecimal costAmount,
                          String currency,
                          boolean estimated,
                          String productName,
                          String usageType,
                          String resourceId,
                          String lineItemType,
                          LocalDate usageStartDate,
                          LocalDate usageEndDate,
                          String recordKey,
                          String dataSource) {
        this.id = UUID.randomUUID();
        this.awsAccountId = awsAccountId;
        this.billingPeriodStart = billingPeriodStart;
        this.billingPeriodEnd = billingPeriodEnd;
        this.serviceName = serviceName;
        this.region = region;
        this.productName = productName;
        this.usageType = usageType;
        this.resourceId = resourceId;
        this.lineItemType = lineItemType;
        this.usageStartDate = usageStartDate;
        this.usageEndDate = usageEndDate;
        this.recordKey = recordKey;
        this.usageAmount = usageAmount;
        this.costAmount = costAmount;
        this.currency = currency;
        this.estimated = estimated;
        this.dataSource = dataSource;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void update(BigDecimal usageAmount, BigDecimal costAmount, String currency, boolean estimated) {
        this.usageAmount = usageAmount;
        this.costAmount = costAmount;
        this.currency = currency;
        this.estimated = estimated;
        this.updatedAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getAwsAccountId() {
        return awsAccountId;
    }

    public LocalDate getBillingPeriodStart() {
        return billingPeriodStart;
    }

    public LocalDate getBillingPeriodEnd() {
        return billingPeriodEnd;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getRegion() {
        return region;
    }

    public String getProductName() { return productName; }
    public String getUsageType() { return usageType; }
    public String getResourceId() { return resourceId; }
    public String getLineItemType() { return lineItemType; }
    public LocalDate getUsageStartDate() { return usageStartDate; }
    public LocalDate getUsageEndDate() { return usageEndDate; }
    public String getRecordKey() { return recordKey; }

    public BigDecimal getUsageAmount() {
        return usageAmount;
    }

    public BigDecimal getCostAmount() {
        return costAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public boolean isEstimated() {
        return estimated;
    }

    public String getDataSource() { return dataSource; }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
