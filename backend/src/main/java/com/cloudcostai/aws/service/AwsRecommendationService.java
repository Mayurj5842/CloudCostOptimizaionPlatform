package com.cloudcostai.aws.service;

import com.cloudcostai.aws.dto.response.AwsRecommendationResponse;

public interface AwsRecommendationService {
    AwsRecommendationResponse getRecommendations(String connectionToken);
    AwsRecommendationResponse generateRecommendations(String connectionToken);
    AwsRecommendationResponse updateStatus(String connectionToken, String recommendationId, String status);
}