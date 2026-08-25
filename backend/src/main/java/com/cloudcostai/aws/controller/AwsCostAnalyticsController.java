package com.cloudcostai.aws.controller;

import com.cloudcostai.aws.dto.response.AwsCostAnalyticsResponse;
import com.cloudcostai.aws.service.AwsCostAnalyticsService;
import com.cloudcostai.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/aws")
public class AwsCostAnalyticsController {
    private final AwsCostAnalyticsService costAnalyticsService;

    public AwsCostAnalyticsController(AwsCostAnalyticsService costAnalyticsService) {
        this.costAnalyticsService = costAnalyticsService;
    }

    @GetMapping("/cost-analytics")
    public ResponseEntity<ApiResponse<AwsCostAnalyticsResponse>> analytics(
            @RequestHeader("X-AWS-Connection-Token") String connectionToken,
            @RequestParam(defaultValue = "CURRENT_MONTH") String period) {
        return ResponseEntity.ok(new ApiResponse<>(true, "AWS cost analytics fetched successfully",
                costAnalyticsService.getAnalytics(connectionToken, period)));
    }
}