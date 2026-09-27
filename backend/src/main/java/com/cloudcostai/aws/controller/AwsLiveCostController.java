package com.cloudcostai.aws.controller;

import com.cloudcostai.aws.dto.response.AwsLiveCostResponse;
import com.cloudcostai.aws.service.AwsLiveCostService;
import com.cloudcostai.common.response.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/aws")
@ConditionalOnProperty(name = "billing.cost-explorer.enabled", havingValue = "true")
public class AwsLiveCostController {
    private final AwsLiveCostService liveCostService;

    public AwsLiveCostController(AwsLiveCostService liveCostService) {
        this.liveCostService = liveCostService;
    }

    @GetMapping("/live-cost")
    public ResponseEntity<ApiResponse<AwsLiveCostResponse>> fetch(
            @RequestHeader("X-AWS-Connection-Token") String connectionToken,
            @RequestParam String month) {
        AwsLiveCostResponse response = liveCostService.fetch(connectionToken, month);
        return ResponseEntity.ok(new ApiResponse<>("SUCCESS".equals(response.status()),
                response.message() == null ? "Live AWS cost fetched successfully" : response.message(), response));
    }
}
