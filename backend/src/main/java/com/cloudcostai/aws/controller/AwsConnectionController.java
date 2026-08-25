package com.cloudcostai.aws.controller;

import com.cloudcostai.aws.dto.request.AwsConnectionRequest;
import com.cloudcostai.aws.dto.response.AwsConnectionResponse;
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

        var identity = awsConnectionService.verifyConnection(
                request.accessKey(),
                request.secretKey(),
                request.region()
        );

        AwsAccount account = awsAccountRepository
                .findByUserIdAndProvider(identity.userId(), "AWS")
                .orElseGet(() -> new AwsAccount(
                        identity.userId(), request.accountAlias(), request.region(), identity.accountId(),
                        identity.arn(), "AWS", "CONNECTED"));
        account.markConnected(request.accountAlias(), request.region(), identity.accountId(), identity.arn());
        awsAccountRepository.save(account);

        // Invalidate any existing active session for this AWS account before creating a new one.
        sessionService.invalidateByAccountId(identity.accountId());
        String connectionToken = sessionService.createSession(
                request.accessKey(), request.secretKey(), request.region(), identity);

        AwsConnectionResponse response = new AwsConnectionResponse(
                identity.accountId(),
                identity.arn(),
                identity.userId(),
                request.region(),
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

        @GetMapping("/connection")
        public ResponseEntity<ApiResponse<AwsConnectionResponse>> getConnection() {
                var account = awsAccountRepository
                                .findFirstByProviderAndConnectionStatusOrderByUpdatedAtDesc("AWS", "CONNECTED");
                if (account.isEmpty()) {
                        return ResponseEntity.ok(new ApiResponse<>(true, "No AWS account is connected", null));
                }

                AwsAccount connectedAccount = account.get();
                String connectionToken = sessionService.findTokenByAccountId(connectedAccount.getAccountId()).orElse(null);
            if (connectionToken == null) {
                    // The database still marks this AWS account as connected, but the session has expired or was removed.
                    connectedAccount.markDisconnected();
                    awsAccountRepository.save(connectedAccount);
                    return ResponseEntity.ok(new ApiResponse<>(true, "No AWS account is connected", null));
            }

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
        public ResponseEntity<ApiResponse<Void>> disconnect() {
                awsAccountRepository
                                .findFirstByProviderAndConnectionStatusOrderByUpdatedAtDesc("AWS", "CONNECTED")
                                .ifPresent(account -> {
                                        account.markDisconnected();
                                        awsAccountRepository.save(account);
                                        sessionService.invalidateByAccountId(account.getAccountId());
                                });
                return ResponseEntity.ok(new ApiResponse<>(true, "AWS account disconnected", null));
        }
}