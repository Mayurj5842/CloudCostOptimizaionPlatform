package com.cloudcostai.aws.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record CostSimulationResponse(
        String accountId,
        String billingMonth,
        String currency,
        String dataSource,
        String status,
        String message,
        BigDecimal currentMonthlyCost,
        BigDecimal estimatedMonthlyCost,
        BigDecimal estimatedMonthlySaving,
        BigDecimal estimatedAnnualSaving,
        List<Scenario> scenarios
) {
    public record Scenario(String recommendationId, String title, String service, String resourceId,
                           String region, String status, BigDecimal estimatedMonthlySaving, String message) {}
}