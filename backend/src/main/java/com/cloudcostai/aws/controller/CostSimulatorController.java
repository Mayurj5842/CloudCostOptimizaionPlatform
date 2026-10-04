package com.cloudcostai.aws.controller;

import com.cloudcostai.aws.dto.request.CostSimulationRequest;
import com.cloudcostai.aws.dto.response.CostSimulationResponse;
import com.cloudcostai.aws.service.CostSimulatorService;
import com.cloudcostai.common.response.ApiResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/aws/optimization")
public class CostSimulatorController {
    private final CostSimulatorService simulatorService;

    public CostSimulatorController(CostSimulatorService simulatorService) {
        this.simulatorService = simulatorService;
    }

    @PostMapping("/simulator")
    public ResponseEntity<ApiResponse<CostSimulationResponse>> simulate(
            @RequestHeader("X-AWS-Connection-Token") String connectionToken,
            @RequestBody CostSimulationRequest request) {
        try {
            CostSimulationResponse result = simulatorService.simulate(connectionToken, request);
            return ResponseEntity.ok(new ApiResponse<>("SUCCESS".equals(result.status()),
                    result.message() == null ? "Cost simulation completed" : result.message(), result));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, exception.getMessage(), null));
        } catch (DataAccessException exception) {
            return ResponseEntity.internalServerError().body(new ApiResponse<>(false,
                    "Persisted billing or recommendation data could not be loaded.", null));
        }
    }
}