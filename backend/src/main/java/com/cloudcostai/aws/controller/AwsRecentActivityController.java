package com.cloudcostai.aws.controller;

import com.cloudcostai.aws.dto.response.AwsRecentActivityResponse;
import com.cloudcostai.aws.service.AwsRecentActivityService;
import com.cloudcostai.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/aws")
public class AwsRecentActivityController {
    private final AwsRecentActivityService recentActivityService;

    public AwsRecentActivityController(AwsRecentActivityService recentActivityService) {
        this.recentActivityService = recentActivityService;
    }

    @GetMapping("/recent-activity")
    public ResponseEntity<ApiResponse<AwsRecentActivityResponse>> getRecentActivity(
            @RequestHeader("X-AWS-Connection-Token") String connectionToken,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Recent AWS activity fetched successfully",
                recentActivityService.getRecentActivity(connectionToken, limit)));
    }
}
