package com.cloudcostai.aws.controller;

import com.cloudcostai.aws.dto.response.AwsResourceResponse;
import com.cloudcostai.aws.service.AwsResourceService;
import com.cloudcostai.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/aws")
public class AwsResourceController {
    private final AwsResourceService awsResourceService;

    public AwsResourceController(AwsResourceService awsResourceService) {
        this.awsResourceService = awsResourceService;
    }

    @GetMapping("/resources")
    public ResponseEntity<ApiResponse<AwsResourceResponse>> resources(
            @RequestHeader("X-AWS-Connection-Token") String connectionToken) {
        return ResponseEntity.ok(new ApiResponse<>(true, "AWS resources fetched successfully",
                awsResourceService.discoverResources(connectionToken)));
    }
}
