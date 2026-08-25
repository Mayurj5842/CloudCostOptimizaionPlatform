package com.cloudcostai.aws.dto.response;

import java.math.BigDecimal;

public record AwsBillSummaryResponse(
        String accountId,
        BillingPeriod billingPeriod,
        String billStatus,
        String currency,
        String serviceProvider,
        BigDecimal totalAmount,
        BigDecimal estimatedGrandTotal,
        String paymentInformation,
        String lastSyncedAt
) {
    public record BillingPeriod(String start, String end) {}
}
