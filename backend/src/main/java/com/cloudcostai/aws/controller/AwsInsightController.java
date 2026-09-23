package com.cloudcostai.aws.controller;

import com.cloudcostai.aws.dto.response.AwsInsightResponse;
import com.cloudcostai.aws.exception.AwsConnectionException;
import com.cloudcostai.aws.service.AwsInsightService;
import com.cloudcostai.common.response.ApiResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/aws")
public class AwsInsightController {
    private final AwsInsightService insightService;

    public AwsInsightController(AwsInsightService insightService) {
        this.insightService = insightService;
    }

    @GetMapping("/insights")
    public ResponseEntity<ApiResponse<AwsInsightResponse>> insights(
            @RequestHeader("X-AWS-Connection-Token") String connectionToken,
            @RequestParam(required = false) String month) {
        try {
            AwsInsightResponse response = insightService.getInsights(connectionToken, month);
            boolean success = !"NO_CUR_DATA".equals(response.status());
            return ResponseEntity.ok(new ApiResponse<>(success, response.message() == null ? "AWS insights generated successfully" : response.message(), response));
        } catch (AwsConnectionException exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(false, "AWS connection session is unavailable.",
                    error("INVALID_SESSION", "AWS connection session is unavailable.")));
        } catch (DataAccessException exception) {
            return ResponseEntity.internalServerError().body(new ApiResponse<>(false, "Billing data could not be loaded. Please try again.",
                    error("DATA_ERROR", "Billing data could not be loaded. Please try again.")));
        } catch (RuntimeException exception) {
            return ResponseEntity.internalServerError().body(new ApiResponse<>(false, "Insights could not be calculated from the available billing data.",
                    error("INSIGHT_CALCULATION_ERROR", "Insights could not be calculated from the available billing data.")));
        }
    }

    private AwsInsightResponse error(String status, String message) {
        return new AwsInsightResponse(null, "USD", java.time.LocalDateTime.now().toString(), java.util.List.of(), java.util.List.of(),
                status, "CUR_POSTGRESQL", null, null, null, null, message);
    }
}