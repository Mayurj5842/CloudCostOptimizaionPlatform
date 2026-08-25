package com.cloudcostai.aws.controller;

import com.cloudcostai.aws.dto.response.AwsBillSummaryResponse;
import com.cloudcostai.aws.dto.response.AwsBillingServiceCostResponse;
import com.cloudcostai.aws.service.AwsBillService;
import com.cloudcostai.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/billing")
public class AwsBillingController {
    private final AwsBillService billService;

    public AwsBillingController(AwsBillService billService) {
        this.billService = billService;
    }

    @GetMapping("/bills")
    public ResponseEntity<ApiResponse<AwsBillSummaryResponse>> getBills(
            @RequestHeader("X-AWS-Connection-Token") String connectionToken
    ) {
        return ResponseEntity.ok(new ApiResponse<>(true, "AWS bill summary fetched successfully",
                billService.getBillSummary(connectionToken)));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AwsBillSummaryResponse>> getSummary(
            @RequestHeader("X-AWS-Connection-Token") String connectionToken
    ) {
        return ResponseEntity.ok(new ApiResponse<>(true, "AWS billing summary fetched successfully",
                billService.getBillSummary(connectionToken)));
    }

    @GetMapping("/service-breakdown")
    public ResponseEntity<ApiResponse<List<AwsBillingServiceCostResponse>>> getServiceBreakdown(
            @RequestHeader("X-AWS-Connection-Token") String connectionToken
    ) {
        return ResponseEntity.ok(new ApiResponse<>(true, "AWS service breakdown fetched successfully",
                billService.getServiceBreakdown(connectionToken)));
    }

        @PostMapping("/sync")
        public ResponseEntity<ApiResponse<AwsBillSummaryResponse>> syncBilling(
                @RequestHeader("X-AWS-Connection-Token") String connectionToken
        ) {
        return ResponseEntity.ok(new ApiResponse<>(
                true,
                "AWS billing synchronization completed successfully",
                billService.syncBilling(connectionToken)
        ));
        }
}
