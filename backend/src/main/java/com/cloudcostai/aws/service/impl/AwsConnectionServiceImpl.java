package com.cloudcostai.aws.service.impl;

import com.cloudcostai.aws.config.AwsClientFactory;
import com.cloudcostai.aws.exception.AwsConnectionException;
import com.cloudcostai.aws.service.AwsConnectionService;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityRequest;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityResponse;
import software.amazon.awssdk.services.sts.model.StsException;

@Service
public class AwsConnectionServiceImpl implements AwsConnectionService {

    private final AwsClientFactory awsClientFactory;

    public AwsConnectionServiceImpl(AwsClientFactory awsClientFactory) {
        this.awsClientFactory = awsClientFactory;
    }

    @Override
    public AwsIdentity verifyConnection(
            String accessKey,
            String secretKey,
            String region
    ) {
        try (StsClient stsClient =
                     awsClientFactory.createStsClient(
                             accessKey,
                             secretKey,
                             region
                     )) {

            GetCallerIdentityResponse identity =
                    stsClient.getCallerIdentity(
                            GetCallerIdentityRequest.builder().build()
                    );

            return new AwsIdentity(
                    identity.account(),
                    identity.arn(),
                    identity.userId()
            );

        } catch (StsException exception) {
            throw new AwsConnectionException(
                    "Unable to authenticate with AWS. Please verify the credentials and region.",
                    exception
            );
        } catch (RuntimeException exception) {
            throw new AwsConnectionException(
                    "Unable to connect to AWS.",
                    exception
            );
        }
    }

    public record AwsIdentity(
            String accountId,
            String arn,
            String userId
    ) {
    }
}