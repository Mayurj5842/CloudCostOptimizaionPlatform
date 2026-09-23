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
@RequestMapping("/api/aws")
public class AwsRecommendationController {
    private final AwsRecommendationService recommendationService;

    public AwsRecommendationController(AwsRecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/recommendations")
    public ResponseEntity<ApiResponse<AwsRecommendationResponse>> recommendations(
            @RequestHeader("X-AWS-Connection-Token") String connectionToken,
            @RequestParam(required = false) String month) {
        return ResponseEntity.ok(new ApiResponse<>(true, "AWS recommendations generated successfully",
                recommendationService.getRecommendations(connectionToken, month)));
    }

    @PostMapping("/recommendations/generate")
    public ResponseEntity<ApiResponse<AwsRecommendationResponse>> generate(
            @RequestHeader("X-AWS-Connection-Token") String connectionToken,
            @RequestParam(required = false) String month) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Recommendations generated from PostgreSQL billing data",
                recommendationService.generateRecommendations(connectionToken, month)));
    }

    @PutMapping("/recommendations/{recommendationId}/status")
    public ResponseEntity<ApiResponse<AwsRecommendationResponse>> status(
            @RequestHeader("X-AWS-Connection-Token") String connectionToken,
            @PathVariable String recommendationId,
            @RequestParam String value,
            @RequestParam(required = false) String month) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Recommendation status updated",
                recommendationService.updateStatus(connectionToken, recommendationId, value, month)));
    }
}