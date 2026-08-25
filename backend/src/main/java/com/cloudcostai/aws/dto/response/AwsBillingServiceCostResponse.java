package com.cloudcostai.aws.dto.response;

import java.math.BigDecimal;

public record AwsBillingServiceCostResponse(
        String serviceName,
        BigDecimal costAmount,
        String currency
) {
}
