package com.cloudcostai.aws.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record AwsDashboardChartResponse(String accountId, String currency, String range, List<CostPoint> points,
                                        List<ServicePoint> services, BigDecimal totalCost, String dataStatus, String dataSource) {
    public AwsDashboardChartResponse(String accountId, String currency, String range, List<CostPoint> points,
                                     List<ServicePoint> services, BigDecimal totalCost, String dataStatus) {
        this(accountId, currency, range, points, services, totalCost, dataStatus, "CUR_2_0");
    }
    public record CostPoint(String period, BigDecimal cost) {}
    public record ServicePoint(String service, BigDecimal cost, BigDecimal percentage) {}
}