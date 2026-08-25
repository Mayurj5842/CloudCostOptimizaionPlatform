package com.cloudcostai.aws.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record AwsDashboardResponse(
        String accountId,
        Spend totalCloudSpend,
        ActiveResources activeResources,
        Savings estimatedSavings,
        BudgetUsage budgetUsage,
        List<CostPoint> costTrend,
        List<ServiceCost> serviceDistribution
) {
    public record Spend(BigDecimal value, String currency, String period, BigDecimal previousMonth,
                        BigDecimal percentageChange) {}

    public record ActiveResources(int value) {}

    public record Savings(BigDecimal value, String currency, String period) {}

    public record BudgetUsage(boolean configured, BigDecimal usagePercentage, BigDecimal budgetLimit,
                              BigDecimal actualSpend, String currency, String status) {}

    public record CostPoint(String month, BigDecimal cost) {}

    public record ServiceCost(String name, BigDecimal value) {}
}