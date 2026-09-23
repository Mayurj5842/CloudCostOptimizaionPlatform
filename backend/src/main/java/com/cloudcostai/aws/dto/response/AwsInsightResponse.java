package com.cloudcostai.aws.dto.response;

import java.math.BigDecimal;
import java.util.List;
import com.cloudcostai.aws.dto.response.AwsRecommendationResponse.Recommendation;

public record AwsInsightResponse(
        String accountId,
        String currency,
        String generatedAt,
                List<Insight> insights,
                List<Recommendation> recommendations,
                String status,
                String dataSource,
                String billingPeriodStart,
                String billingPeriodEnd,
                String lastUpdated,
                BigDecimal totalSpend,
                String message
) {
        public AwsInsightResponse(String accountId, String currency, String generatedAt, List<Insight> insights) {
                this(accountId, currency, generatedAt, insights, List.of(), "SUCCESS", "CUR_POSTGRESQL", null, null, generatedAt, null, null);
        }

    public record Insight(
            String id,
            String severity,
            String category,
            String service,
            String region,
            String resource,
            BigDecimal currentCost,
            String problemDetected,
            String reason,
            BigDecimal estimatedImpact,
            String recommendedAction,
            String resourcePath
    ) {}
}