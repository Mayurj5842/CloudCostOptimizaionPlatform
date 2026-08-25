package com.cloudcostai.aws.controller;

import com.cloudcostai.aws.dto.response.AwsBillSummaryResponse;
import com.cloudcostai.aws.service.AwsBillService;
import com.cloudcostai.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/aws")
public class AwsBillController {
    private final AwsBillService billService;

    public AwsBillController(AwsBillService billService) {
        this.billService = billService;
    }

    @GetMapping("/bills")
    public ResponseEntity<ApiResponse<AwsBillSummaryResponse>> getBills(
            @RequestHeader("X-AWS-Connection-Token") String connectionToken
    ) {
        return ResponseEntity.ok(new ApiResponse<>(true, "AWS bill summary fetched successfully",
                billService.getBillSummary(connectionToken)));
    }
}
