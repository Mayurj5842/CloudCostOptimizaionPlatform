package com.cloudcostai.aws.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record AwsCostAnalyticsResponse(
        String accountId,
        String currency,
        Period period,
        Summary summary,
    List<CostPoint> dailyTrend,
    List<DailyServiceCost> dailyServiceCosts,
    List<CostBreakdown> serviceCosts,
        List<CostBreakdown> regionCosts,
        List<CostDriver> topCostDrivers,
        List<CostAnomaly> anomalies,
        Budget budget,
        PotentialSavings potentialSavings,
        String dataStatus
) {
    public record Period(String start, String end) {}

    public record Summary(BigDecimal totalCost, BigDecimal thisMonth, BigDecimal dailyAverage,
                          BigDecimal forecast, BigDecimal savings) {}

    public record CostPoint(String date, BigDecimal cost) {}

    public record CostBreakdown(String name, BigDecimal cost) {}

    public record DailyServiceCost(String date, List<CostBreakdown> services) {}

    public record CostDriver(String service, BigDecimal cost, BigDecimal percentage) {}

    public record CostAnomaly(String date, BigDecimal cost, BigDecimal percentageAboveAverage) {}

    public record Budget(boolean configured, BigDecimal limit, BigDecimal actualSpend,
                         BigDecimal usagePercentage, String currency) {}

    public record PotentialSavings(boolean available, BigDecimal monthlySavings) {}
}