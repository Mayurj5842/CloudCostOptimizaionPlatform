package com.cloudcostai.aws.dto.request;

import java.util.List;
import java.util.UUID;

public record CostSimulationRequest(String month, List<UUID> recommendationIds) {}