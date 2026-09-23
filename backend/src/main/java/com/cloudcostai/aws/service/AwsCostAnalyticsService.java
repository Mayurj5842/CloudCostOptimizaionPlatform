package com.cloudcostai.aws.service;

import com.cloudcostai.aws.dto.response.AwsCostAnalyticsResponse;

public interface AwsCostAnalyticsService {
    AwsCostAnalyticsResponse getAnalytics(String connectionToken, String period);
    AwsCostAnalyticsResponse getAnalytics(String connectionToken, String period, String month);
}