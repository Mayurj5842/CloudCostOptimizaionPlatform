package com.cloudcostai.aws.controller;

import com.cloudcostai.aws.dto.response.AwsDashboardResponse;
import com.cloudcostai.aws.dto.response.AwsDashboardChartResponse;
import com.cloudcostai.aws.service.AwsDashboardService;
import com.cloudcostai.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/aws")
public class AwsDashboardController {
    private final AwsDashboardService dashboardService;

    public AwsDashboardController(AwsDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<AwsDashboardResponse>> dashboard(
            @RequestHeader("X-AWS-Connection-Token") String connectionToken,
            @RequestParam(required = false) String month) {
        return ResponseEntity.ok(new ApiResponse<>(true, "AWS dashboard fetched successfully",
                dashboardService.getDashboard(connectionToken, month)));
    }

    @GetMapping("/dashboard/cost-trend")
    public ResponseEntity<ApiResponse<AwsDashboardChartResponse>> costTrend(
            @RequestHeader("X-AWS-Connection-Token") String connectionToken,
            @RequestParam(defaultValue = "30D") String range,
            @RequestParam(required = false) String month) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Cost trend aggregated from PostgreSQL",
                dashboardService.getCostTrend(connectionToken, range, month)));
    }

    @GetMapping("/dashboard/service-distribution")
    public ResponseEntity<ApiResponse<AwsDashboardChartResponse>> serviceDistribution(
            @RequestHeader("X-AWS-Connection-Token") String connectionToken,
            @RequestParam(defaultValue = "30D") String range,
            @RequestParam(required = false) String month) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Service distribution aggregated from PostgreSQL",
                dashboardService.getServiceDistribution(connectionToken, range, month)));
    }
}