package com.cloudcostai.aws.controller;

import com.cloudcostai.aws.dto.response.AwsInsightResponse;
import com.cloudcostai.aws.service.AwsInsightService;
import com.cloudcostai.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/aws")
public class AwsInsightController {
    private final AwsInsightService insightService;

    public AwsInsightController(AwsInsightService insightService) {
        this.insightService = insightService;
    }

    @GetMapping("/insights")
    public ResponseEntity<ApiResponse<AwsInsightResponse>> insights(
            @RequestHeader("X-AWS-Connection-Token") String connectionToken) {
        return ResponseEntity.ok(new ApiResponse<>(true, "AWS insights generated successfully",
                insightService.getInsights(connectionToken)));
    }
}