package com.cloudcostai.aws.controller;

import com.cloudcostai.aws.dto.request.AwsConnectionRequest;
import com.cloudcostai.aws.dto.response.AwsConnectionResponse;
import com.cloudcostai.aws.dto.response.AwsConnectionTestResponse;
import com.cloudcostai.aws.service.AwsConnectionService;
import com.cloudcostai.aws.service.AwsConnectionSessionService;
import com.cloudcostai.aws.model.AwsAccount;
import com.cloudcostai.aws.repository.AwsAccountRepository;
import com.cloudcostai.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/aws")
public class AwsConnectionController {

    private final AwsConnectionService awsConnectionService;
        private final AwsAccountRepository awsAccountRepository;
        private final AwsConnectionSessionService sessionService;

                public AwsConnectionController(AwsConnectionService awsConnectionService,
                                                                           AwsConnectionSessionService sessionService,
                                                                           AwsAccountRepository awsAccountRepository) {
        this.awsConnectionService = awsConnectionService;
                this.sessionService = sessionService;
                                this.awsAccountRepository = awsAccountRepository;
    }

    @PostMapping("/connect")
    public ResponseEntity<ApiResponse<AwsConnectionResponse>> connect(
            @Valid @RequestBody AwsConnectionRequest request
    ) {
        // Trim all inputs to ensure no whitespace issues
        String trimmedAccessKey = request.accessKey().trim();
        String trimmedSecretKey = request.secretKey().trim();
        String trimmedRegion = request.region().trim();

        var identity = awsConnectionService.verifyConnection(
                trimmedAccessKey,
                trimmedSecretKey,
                trimmedRegion
        );

        AwsAccount account = awsAccountRepository
                .findByUserIdAndProvider(identity.userId(), "AWS")
                .orElseGet(() -> new AwsAccount(
                        identity.userId(), request.accountAlias(), trimmedRegion, identity.accountId(),
                        identity.arn(), "AWS", "CONNECTED"));
        account.markConnected(request.accountAlias(), trimmedRegion, identity.accountId(), identity.arn());
        awsAccountRepository.save(account);

        // Invalidate any existing active session for this AWS account before creating a new one.
        sessionService.invalidateByAccountId(identity.accountId());
        String connectionToken = sessionService.createSession(
                trimmedAccessKey, trimmedSecretKey, trimmedRegion, identity);

        AwsConnectionResponse response = new AwsConnectionResponse(
                identity.accountId(),
                identity.arn(),
                identity.userId(),
                trimmedRegion,
                "CONNECTED",
                request.accountAlias(),
                "AWS",
                connectionToken
        );

        ApiResponse<AwsConnectionResponse> apiResponse =
                new ApiResponse<>(
                        true,
                        "Successfully connected to AWS",
                        response
                );

        return ResponseEntity.ok(apiResponse);
    }

        @GetMapping("/connection/test")
        public ResponseEntity<ApiResponse<AwsConnectionTestResponse>> testConnection(
                        @RequestHeader("X-AWS-Connection-Token") String connectionToken) {
                var session = sessionService.getSession(connectionToken);
                return ResponseEntity.ok(new ApiResponse<>(true, "AWS connection is active",
                                new AwsConnectionTestResponse(session.accountId(), session.region(), "HEALTHY",
                                                "The existing AWS session is valid.")));
        }

        @GetMapping("/connection")
        public ResponseEntity<ApiResponse<AwsConnectionResponse>> getConnection(
                        @RequestHeader("X-AWS-Connection-Token") String connectionToken) {
                var session = sessionService.getSession(connectionToken);
                var account = awsAccountRepository.findByAccountIdAndProviderAndConnectionStatus(
                                session.accountId(), "AWS", "CONNECTED");
                if (account.isEmpty()) {
                        return ResponseEntity.ok(new ApiResponse<>(true, "No AWS account is connected", null));
                }

                AwsAccount connectedAccount = account.get();

                AwsConnectionResponse response = new AwsConnectionResponse(
                                connectedAccount.getAccountId(),
                                connectedAccount.getIamArn(),
                                connectedAccount.getUserId(),
                                connectedAccount.getRegion(),
                                connectedAccount.getConnectionStatus(),
                                connectedAccount.getAccountAlias(),
                                connectedAccount.getProvider(),
                                connectionToken
                );

                return ResponseEntity.ok(new ApiResponse<>(true, "AWS account connection loaded", response));
        }

        @PostMapping("/disconnect")
        public ResponseEntity<ApiResponse<Void>> disconnect(
                        @RequestHeader("X-AWS-Connection-Token") String connectionToken) {
                var session = sessionService.getSession(connectionToken);
                awsAccountRepository
                                .findByAccountIdAndProviderAndConnectionStatus(session.accountId(), "AWS", "CONNECTED")
                                .ifPresent(account -> {
                                        account.markDisconnected();
                                        awsAccountRepository.save(account);
                                        sessionService.invalidateByAccountId(account.getAccountId());
                                });
                return ResponseEntity.ok(new ApiResponse<>(true, "AWS account disconnected", null));
        }
}