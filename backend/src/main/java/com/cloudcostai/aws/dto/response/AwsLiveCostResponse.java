package com.cloudcostai.aws.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record AwsLiveCostResponse(
        String accountId,
        String billingPeriodStart,
        String billingPeriodEnd,
        String currency,
        BigDecimal cost,
        List<RegionCost> regions,
        List<ServiceCost> services,
        String dataSource,
        String fetchedAt,
        String status,
        String message
) {
    public record RegionCost(String region, BigDecimal cost, String currency, String fetchedAt) {}
    public record ServiceCost(String service, BigDecimal cost, String currency, String fetchedAt) {}
}
