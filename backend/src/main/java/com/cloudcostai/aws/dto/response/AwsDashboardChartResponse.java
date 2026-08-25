package com.cloudcostai.aws.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record AwsDashboardChartResponse(String accountId, String currency, String range, List<CostPoint> points,
                                        List<ServicePoint> services, BigDecimal totalCost) {
    public record CostPoint(String period, BigDecimal cost) {}
    public record ServicePoint(String service, BigDecimal cost, BigDecimal percentage) {}
}