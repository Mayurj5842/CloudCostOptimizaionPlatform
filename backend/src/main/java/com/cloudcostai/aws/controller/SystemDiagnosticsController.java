package com.cloudcostai.aws.controller;

import com.cloudcostai.aws.config.AwsBillingSyncProperties;
import com.cloudcostai.aws.config.AwsClientFactory;
import com.cloudcostai.aws.dto.response.SystemDiagnosticsResponse;
import com.cloudcostai.aws.repository.AwsBillingRepository;
import com.cloudcostai.aws.service.AwsConnectionSessionService;
import com.cloudcostai.aws.service.AwsResourceService;
import com.cloudcostai.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/system")
public class SystemDiagnosticsController {
    private final AwsConnectionSessionService sessionService;
    private final AwsBillingRepository billingRepository;
    private final AwsResourceService resourceService;
    private final AwsBillingSyncProperties billingProperties;
    private final AwsClientFactory clientFactory;

    public SystemDiagnosticsController(AwsConnectionSessionService sessionService,
                                       AwsBillingRepository billingRepository,
                                       AwsResourceService resourceService,
                                       AwsBillingSyncProperties billingProperties,
                                       AwsClientFactory clientFactory) {
        this.sessionService = sessionService;
        this.billingRepository = billingRepository;
        this.resourceService = resourceService;
        this.billingProperties = billingProperties;
        this.clientFactory = clientFactory;
    }

    @GetMapping("/diagnostics")
    public ResponseEntity<ApiResponse<SystemDiagnosticsResponse>> diagnostics(
            @RequestHeader("X-AWS-Connection-Token") String connectionToken) {
        List<SystemDiagnosticsResponse.DiagnosticItem> checks = new ArrayList<>();
        checks.add(check("backend-api", "Backend API", "HEALTHY", "Diagnostics request completed."));

        String accountId = null;
        try {
            var session = sessionService.getSession(connectionToken);
            accountId = session.accountId();
            checks.add(check("aws-session", "AWS Session", "HEALTHY", "Session is valid."));
            checks.add(check("aws-region", "AWS Region", "HEALTHY", session.region()));
            try (StsClient stsClient = clientFactory.createStsClient(session.accessKey(), session.secretKey(), session.region())) {
                var identity = stsClient.getCallerIdentity(GetCallerIdentityRequest.builder().build());
                checks.add(check("aws-connectivity", "AWS Connectivity", "HEALTHY",
                        "AWS authenticated account " + identity.account() + " is reachable."));
            } catch (RuntimeException awsException) {
                checks.add(check("aws-connectivity", "AWS Connectivity", "FAILED",
                        failureDetail("AWS authentication or connectivity failed", awsException)));
            }
        } catch (RuntimeException exception) {
            checks.add(check("aws-session", "AWS Session", "FAILED", "The AWS session could not be validated."));
            checks.add(check("aws-region", "AWS Region", "UNAVAILABLE", "AWS region is unavailable."));
            checks.add(check("aws-connectivity", "AWS Connectivity", "UNAVAILABLE", "AWS connectivity could not be checked because the session is invalid."));
        }

        try {
            billingRepository.count();
            checks.add(check("postgresql", "PostgreSQL", "HEALTHY", "Database query completed."));
        } catch (RuntimeException exception) {
            checks.add(check("postgresql", "PostgreSQL", "FAILED", "Database query failed."));
        }

        boolean curConfigured = present(billingProperties.getBucket()) && present(billingProperties.getPrefix());
        checks.add(check("cur-configuration", "CUR 2.0 Configuration", curConfigured ? "HEALTHY" : "WARNING",
                curConfigured ? "CUR S3 bucket and prefix are configured." : "CUR S3 bucket or prefix is not configured."));

        if (accountId == null) {
            checks.add(check("billing-data", "S3 / Billing Data", "UNAVAILABLE", "Connect AWS to check billing data."));
            checks.add(check("resource-inventory", "Resource Inventory", "UNAVAILABLE", "Connect AWS to check resources."));
        } else {
            try {
                boolean hasBilling = !billingRepository.findByAwsAccountId(accountId).isEmpty();
                checks.add(check("billing-data", "S3 / Billing Data", hasBilling ? "HEALTHY" : "WARNING",
                        hasBilling ? "Imported CUR billing data is available." : "No imported CUR billing data is available."));
            } catch (RuntimeException exception) {
                checks.add(check("billing-data", "S3 / Billing Data", "UNAVAILABLE", "Billing data could not be checked."));
            }
            try {
                resourceService.discoverResources(connectionToken);
                checks.add(check("aws-permissions", "AWS Permissions", "HEALTHY", "Required AWS resource permissions are available."));
                checks.add(check("resource-inventory", "Resource Inventory", "HEALTHY", "AWS resource inventory is reachable."));
            } catch (RuntimeException exception) {
                checks.add(check("aws-permissions", "AWS Permissions", "FAILED",
                        failureDetail("Required AWS permissions could not be verified", exception)));
                checks.add(check("resource-inventory", "Resource Inventory", "FAILED",
                        failureDetail("AWS resource inventory is unavailable", exception)));
            }
        }

        return ResponseEntity.ok(new ApiResponse<>(true, "System diagnostics completed",
                new SystemDiagnosticsResponse(Instant.now(), List.copyOf(checks))));
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private SystemDiagnosticsResponse.DiagnosticItem check(String key, String label, String status, String detail) {
        return new SystemDiagnosticsResponse.DiagnosticItem(key, label, status, detail);
    }

    private String failureDetail(String prefix, RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? prefix + "." : prefix + ": " + message;
    }
}