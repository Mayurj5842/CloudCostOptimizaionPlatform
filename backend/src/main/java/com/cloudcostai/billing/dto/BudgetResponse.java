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
        LocalDateTime updatedAt
) {}
