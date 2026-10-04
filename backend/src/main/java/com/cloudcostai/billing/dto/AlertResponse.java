package com.cloudcostai.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record AlertResponse(
        UUID id,
        UUID budgetId,
        String type,
        Integer threshold,
        String message,
        BigDecimal actualSpend,
        BigDecimal budgetAmount,
        String currency,
        boolean read,
        LocalDateTime createdAt,
        String severity,
        String title,
        String budgetName,
        String serviceName,
        String alertPeriod,
        String comparisonPeriod,
        BigDecimal percentageChange
) {
    public AlertResponse(UUID id, UUID budgetId, String type, Integer threshold, String message,
                         BigDecimal actualSpend, BigDecimal budgetAmount, String currency, boolean read,
                         LocalDateTime createdAt) {
        this(id, budgetId, type, threshold, message, actualSpend, budgetAmount, currency, read, createdAt,
                "WARNING", message, null, null, null, null, null);
    }
}
