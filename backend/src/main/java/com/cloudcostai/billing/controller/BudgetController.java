package com.cloudcostai.billing.controller;

import com.cloudcostai.billing.dto.AlertResponse;
import com.cloudcostai.billing.dto.BudgetRequest;
import com.cloudcostai.billing.dto.BudgetResponse;
import com.cloudcostai.billing.service.BudgetService;
import com.cloudcostai.aws.service.AwsConnectionSessionService;
import com.cloudcostai.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.time.LocalDate;

@RestController
@RequestMapping("/api")
public class BudgetController {
    private final BudgetService budgetService;
    private final AwsConnectionSessionService sessionService;

    public BudgetController(BudgetService budgetService, AwsConnectionSessionService sessionService) {
        this.budgetService = budgetService;
        this.sessionService = sessionService;
    }

    @GetMapping("/budgets")
    public ResponseEntity<ApiResponse<List<BudgetResponse>>> getBudgets(
            @RequestHeader("X-AWS-Connection-Token") String connectionToken,
            @RequestParam(required = false) String month) {
        String accountId = accountId(connectionToken);
        return ResponseEntity.ok(new ApiResponse<>(true, "Budgets fetched successfully",
                budgetService.getBudgetsForAccount(accountId, month)));
    }

    @GetMapping("/budgets/services")
    public ResponseEntity<ApiResponse<List<String>>> getServices(
            @RequestHeader("X-AWS-Connection-Token") String connectionToken,
            @RequestParam(required = false) String month) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Budget services fetched successfully",
                budgetService.getServicesForAccount(accountId(connectionToken), month)));
    }

    @GetMapping("/budgets/{id}")
    public ResponseEntity<ApiResponse<BudgetResponse>> getBudget(@RequestHeader("X-AWS-Connection-Token") String connectionToken, @PathVariable UUID id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Budget fetched successfully", budgetService.getBudget(id, accountId(connectionToken))));
    }

    @PostMapping("/budgets")
    public ResponseEntity<ApiResponse<BudgetResponse>> createBudget(@RequestHeader("X-AWS-Connection-Token") String connectionToken,
                                                                  @Valid @RequestBody BudgetRequest request) {
        String accountId = accountId(connectionToken);
        if (!accountId.equals(request.awsAccountId())) {
            throw new IllegalArgumentException("Budget account must match the connected AWS account");
        }
        String userId = "system-user";
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(true, "Budget created successfully", budgetService.createBudget(userId, request)));
    }

    @PutMapping("/budgets/{id}")
    public ResponseEntity<ApiResponse<BudgetResponse>> updateBudget(@RequestHeader("X-AWS-Connection-Token") String connectionToken, @PathVariable UUID id, @Valid @RequestBody BudgetRequest request) {
        String accountId = accountId(connectionToken);
        if (!accountId.equals(request.awsAccountId())) {
            throw new IllegalArgumentException("Budget account must match the connected AWS account");
        }
        return ResponseEntity.ok(new ApiResponse<>(true, "Budget updated successfully", budgetService.updateBudget(id, accountId, request)));
    }

    @DeleteMapping("/budgets/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteBudget(@RequestHeader("X-AWS-Connection-Token") String connectionToken, @PathVariable UUID id) {
        budgetService.deleteBudget(id, accountId(connectionToken));
        return ResponseEntity.ok(new ApiResponse<>(true, "Budget deleted successfully", null));
    }

    @GetMapping("/budgets/{id}/usage")
    public ResponseEntity<ApiResponse<BudgetResponse>> getUsage(@RequestHeader("X-AWS-Connection-Token") String connectionToken, @PathVariable UUID id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Budget usage fetched successfully", budgetService.getBudgetUsage(id, accountId(connectionToken))));
    }

    @GetMapping("/alerts")
    public ResponseEntity<ApiResponse<List<AlertResponse>>> getAlerts(@RequestHeader("X-AWS-Connection-Token") String connectionToken,
                                                                       @RequestParam(required = false) String month,
                                                                       @RequestParam(required = false) String severity,
                                                                       @RequestParam(required = false) String type,
                                                                       @RequestParam(required = false) LocalDate from,
                                                                       @RequestParam(required = false) LocalDate to) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Alerts fetched successfully",
            budgetService.getAlertsForAccount(accountId(connectionToken), month, severity, type, from, to)));
    }

    @PatchMapping("/alerts/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAlertRead(@RequestHeader("X-AWS-Connection-Token") String connectionToken, @PathVariable UUID id) {
        budgetService.markAlertRead(id, accountId(connectionToken));
        return ResponseEntity.ok(new ApiResponse<>(true, "Alert marked as read", null));
    }

    private String accountId(String connectionToken) {
        return sessionService.getSession(connectionToken).accountId();
    }
}
