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
        LocalDateTime createdAt
) {}
