package com.cloudcostai.aws.service;

import com.cloudcostai.aws.dto.response.AwsInsightResponse;

import java.math.BigDecimal;

public interface AwsInsightService {
    AwsInsightResponse getInsights(String connectionToken, String month);

    default AwsInsightResponse getInsights(String connectionToken) {
        return getInsights(connectionToken, null);
    }

    BigDecimal estimatedSavings(String connectionToken, String month);
}