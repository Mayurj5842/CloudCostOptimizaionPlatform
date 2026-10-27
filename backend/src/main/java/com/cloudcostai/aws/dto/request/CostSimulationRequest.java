package com.cloudcostai.aws.dto.request;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CostSimulationRequest(String month, List<UUID> recommendationIds, BigDecimal whatIfReductionPercent) {
    public CostSimulationRequest(String month, List<UUID> recommendationIds) {
        this(month, recommendationIds, null);
    }
}
