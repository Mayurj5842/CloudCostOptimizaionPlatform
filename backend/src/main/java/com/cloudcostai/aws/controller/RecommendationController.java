package com.cloudcostai.aws.controller;

import com.cloudcostai.aws.dto.response.AwsRecommendationResponse;
import com.cloudcostai.aws.service.AwsRecommendationService;
import com.cloudcostai.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {
    private final AwsRecommendationService recommendationService;

    public RecommendationController(AwsRecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AwsRecommendationResponse>> get(
            @RequestHeader("X-AWS-Connection-Token") String connectionToken) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Recommendations fetched from PostgreSQL",
                recommendationService.getRecommendations(connectionToken)));
    }

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<AwsRecommendationResponse>> generate(
            @RequestHeader("X-AWS-Connection-Token") String connectionToken) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Recommendations generated from PostgreSQL billing data",
                recommendationService.generateRecommendations(connectionToken)));
    }

    @PutMapping("/{recommendationId}/status")
    public ResponseEntity<ApiResponse<AwsRecommendationResponse>> updateStatus(
            @RequestHeader("X-AWS-Connection-Token") String connectionToken,
            @PathVariable String recommendationId,
            @RequestParam String value) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Recommendation status updated",
                recommendationService.updateStatus(connectionToken, recommendationId, value)));
    }
}