package com.cloudcostai.billing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record BudgetRequest(
        @NotBlank(message = "Budget name is required")
        @Size(min = 1, max = 200, message = "Budget name must be between 1 and 200 characters")
        String name,
        @NotBlank(message = "AWS account is required")
        String awsAccountId,
        @NotNull(message = "Budget amount is required")
        @DecimalMin(value = "0.01", inclusive = true, message = "Budget amount must be greater than 0")
        BigDecimal amount,
        @NotBlank(message = "Currency is required")
        String currency,
        @NotBlank(message = "Budget period is required")
        String period,
        List<Integer> thresholds,
        String month
) {}
