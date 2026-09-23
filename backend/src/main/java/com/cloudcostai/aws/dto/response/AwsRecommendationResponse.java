package com.cloudcostai.aws.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record AwsRecommendationResponse(String accountId, String currency, List<Recommendation> recommendations,
                                       String billingMonth, String status, String message, BigDecimal totalSpend) {
    public AwsRecommendationResponse(String accountId, String currency, List<Recommendation> recommendations) {
        this(accountId, currency, recommendations, null, recommendations.isEmpty() ? "NO_RECOMMENDATIONS" : "SUCCESS", null, null);
    }
    public record Recommendation(String id, String service, String region, String resource,
                                  BigDecimal currentCost, String problem, String reason, String recommendation, String recommendedAction,
                                  BigDecimal estimatedMonthlySaving, String priority, String category, String status,
                                  String createdAt, String resourcePath, String resourceId, String evidence,
                                  String billingMonth) {}
}