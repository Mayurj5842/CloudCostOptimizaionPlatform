package com.cloudcostai.aws.service;

import com.cloudcostai.aws.dto.response.AwsRecommendationResponse;

public interface AwsRecommendationService {
    AwsRecommendationResponse getRecommendations(String connectionToken);
    default AwsRecommendationResponse getRecommendations(String connectionToken, String month) { return getRecommendations(connectionToken); }
    AwsRecommendationResponse generateRecommendations(String connectionToken);
    default AwsRecommendationResponse generateRecommendations(String connectionToken, String month) { return generateRecommendations(connectionToken); }
    AwsRecommendationResponse updateStatus(String connectionToken, String recommendationId, String status);
    default AwsRecommendationResponse updateStatus(String connectionToken, String recommendationId, String status, String month) {
        return updateStatus(connectionToken, recommendationId, status);
    }
}