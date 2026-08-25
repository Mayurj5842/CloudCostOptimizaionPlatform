package com.cloudcostai.aws.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record AwsInsightResponse(
        String accountId,
        String currency,
        String generatedAt,
        List<Insight> insights
) {
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