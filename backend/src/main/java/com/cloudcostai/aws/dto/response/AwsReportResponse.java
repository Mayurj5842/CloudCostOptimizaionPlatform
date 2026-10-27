package com.cloudcostai.aws.dto.response;

import com.cloudcostai.aws.dto.response.AwsCostAnalyticsResponse.CostBreakdown;
import com.cloudcostai.aws.dto.response.AwsCostAnalyticsResponse.CostDriver;
import com.cloudcostai.aws.dto.response.AwsCostAnalyticsResponse.CostPoint;
import com.cloudcostai.aws.dto.response.AwsInsightResponse.Insight;
import com.cloudcostai.aws.dto.response.AwsRecommendationResponse.Recommendation;
import com.cloudcostai.aws.dto.response.AwsResourceResponse.AwsResourceSummary;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import com.cloudcostai.billing.dto.AlertResponse;
import com.cloudcostai.billing.dto.BudgetResponse;

public record AwsReportResponse(
        String accountId,
        String status,
        String dataSource,
        String billingMonth,
        String billingPeriodStart,
        String billingPeriodEnd,
        Instant generatedAt,
        Instant lastUpdated,
        String currency,
        Summary summary,
        List<CostBreakdown> serviceBreakdown,
        List<CostPoint> costTrend,
        List<CostDriver> topCostDrivers,
        AwsResourceSummary resourceSummary,
        String resourceStatus,
        List<Insight> insights,
        List<Recommendation> recommendations,
        SavingsSummary savingsSummary,
        String message,
        List<BudgetResponse> budgets,
        List<AlertResponse> alerts,
        List<String> availableServices,
        List<String> availableRegions
) {
        public AwsReportResponse(String accountId, String status, String dataSource, String billingMonth,
                     String billingPeriodStart, String billingPeriodEnd, Instant generatedAt,
                     Instant lastUpdated, String currency, Summary summary,
                     List<CostBreakdown> serviceBreakdown, List<CostPoint> costTrend,
                     List<CostDriver> topCostDrivers, AwsResourceSummary resourceSummary,
                     String resourceStatus, List<Insight> insights, List<Recommendation> recommendations,
                     SavingsSummary savingsSummary, String message) {
        this(accountId, status, dataSource, billingMonth, billingPeriodStart, billingPeriodEnd, generatedAt,
            lastUpdated, currency, summary, serviceBreakdown, costTrend, topCostDrivers, resourceSummary,
            resourceStatus, insights, recommendations, savingsSummary, message, List.of(), List.of(),
            List.of(), List.of());
        }
    public record Summary(BigDecimal totalCost, BigDecimal previousMonthCost,
                          BigDecimal changeAmount, BigDecimal changePercentage) {}

    public record SavingsSummary(BigDecimal potentialMonthlySavings, boolean available) {}
}