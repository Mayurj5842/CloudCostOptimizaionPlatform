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
        List<ServiceCost> serviceDistribution,
        Billing billing
) {
    public record Spend(BigDecimal value, String currency, String period, BigDecimal previousMonth,
                        BigDecimal percentageChange) {
        public BigDecimal changePercentage() {
            return percentageChange;
        }

        public String changeDirection() {
            if (percentageChange == null) return "NO_COMPARABLE_PREVIOUS_MONTH_SPEND";
            int comparison = percentageChange.signum();
            return comparison > 0 ? "INCREASED" : comparison < 0 ? "DECREASED" : "UNCHANGED";
        }

        public String dataStatus() {
            return value == null ? "NO_CUR_DATA" : "AVAILABLE";
        }

        public boolean hasPreviousMonthData() {
            return previousMonth != null && previousMonth.signum() != 0;
        }
    }

    public record ActiveResources(int value) {}

    public record Savings(BigDecimal value, String currency, String period) {}

    public record BudgetUsage(boolean configured, BigDecimal usagePercentage, BigDecimal budgetLimit,
                              BigDecimal actualSpend, String currency, String status) {}

    public record CostPoint(String month, BigDecimal cost) {}

    public record ServiceCost(String name, BigDecimal value) {}

    public record Billing(String status, String dataSource, String lastUpdated,
                          String billingPeriodStart, String billingPeriodEnd, String message) {}
}