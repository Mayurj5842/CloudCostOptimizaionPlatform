package com.cloudcostai.aws.service;

import com.cloudcostai.aws.dto.response.AwsInsightResponse;

public interface AwsInsightService {
    AwsInsightResponse getInsights(String connectionToken);
}