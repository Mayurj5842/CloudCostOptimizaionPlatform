package com.cloudcostai.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record BudgetResponse(
        UUID id,
        String name,
        String awsAccountId,
        BigDecimal amount,
        BigDecimal actualSpend,
        BigDecimal remainingAmount,
        BigDecimal usagePercentage,
        String currency,
        String status,
        String period,
        List<Integer> thresholds,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String serviceName,
        boolean enabled,
        BigDecimal forecastSpend,
        BigDecimal projectedVariance,
        boolean forecastAvailable,
        String forecastMessage,
        Integer spikeThresholdPercent
) {
    public BudgetResponse(UUID id, String name, String awsAccountId, BigDecimal amount, BigDecimal actualSpend,
                         BigDecimal remainingAmount, BigDecimal usagePercentage, String currency, String status,
                         String period, List<Integer> thresholds, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(id, name, awsAccountId, amount, actualSpend, remainingAmount, usagePercentage, currency, status,
                period, thresholds, createdAt, updatedAt, null, true, null, null, false,
                "Forecast is unavailable for this period.", 50);
    }
}
