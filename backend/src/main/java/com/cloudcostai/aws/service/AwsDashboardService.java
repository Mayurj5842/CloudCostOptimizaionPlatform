package com.cloudcostai.aws.service;

import com.cloudcostai.aws.dto.response.AwsDashboardResponse;
import com.cloudcostai.aws.dto.response.AwsDashboardChartResponse;

public interface AwsDashboardService {
    AwsDashboardResponse getDashboard(String connectionToken, String month);
    AwsDashboardChartResponse getCostTrend(String connectionToken, String range, String month);
    AwsDashboardChartResponse getServiceDistribution(String connectionToken, String range, String month);
}