package com.cloudcostai.aws.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record AwsResourceCostHistoryResponse(
        String accountId,
        String resourceId,
        String status,
        String currency,
        BigDecimal totalCost,
        BigDecimal latestCost,
        String latestCostDate,
        String latestMonth,
        String previousMonth,
        BigDecimal previousPeriodCost,
        BigDecimal change,
        BigDecimal percentageChange,
        List<DailyCost> dailyCosts,
        List<MonthlyCost> monthlyCosts
) {
    public record DailyCost(String date, BigDecimal cost, String currency) {}

    public record MonthlyCost(String month, BigDecimal cost, String currency) {}
}
