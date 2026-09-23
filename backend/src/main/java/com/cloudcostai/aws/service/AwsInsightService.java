package com.cloudcostai.aws.service;

import com.cloudcostai.aws.dto.response.AwsInsightResponse;

public interface AwsInsightService {
    AwsInsightResponse getInsights(String connectionToken, String month);

    default AwsInsightResponse getInsights(String connectionToken) {
        return getInsights(connectionToken, null);
    }
}