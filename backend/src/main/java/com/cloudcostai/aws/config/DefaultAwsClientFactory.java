package com.cloudcostai.aws.config;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.budgets.BudgetsClient;

import java.util.Locale;

@Component
public class DefaultAwsClientFactory implements AwsClientFactory {

        private final AwsBillingSyncProperties billingSyncProperties;

        public DefaultAwsClientFactory(AwsBillingSyncProperties billingSyncProperties) {
                this.billingSyncProperties = billingSyncProperties;
        }

    @Override
    public StsClient createStsClient(
            String accessKey,
            String secretKey,
            String region
    ) {
        Region awsRegion = validateRegion(region);
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);

        return StsClient.builder()
                .region(awsRegion)
                .credentialsProvider(
                        StaticCredentialsProvider.create(credentials)
                )
                .build();
    }

        @Override
        public Ec2Client createEc2Client(String accessKey, String secretKey, String region) {
                return Ec2Client.builder().region(validateRegion(region)).credentialsProvider(credentials(accessKey, secretKey)).build();
        }

        @Override
        public S3Client createS3Client(String accessKey, String secretKey, String region) {
                return S3Client.builder().region(validateRegion(region)).credentialsProvider(credentials(accessKey, secretKey)).build();
        }

        @Override
        public RdsClient createRdsClient(String accessKey, String secretKey, String region) {
                return RdsClient.builder().region(validateRegion(region)).credentialsProvider(credentials(accessKey, secretKey)).build();
        }

        @Override
        public LambdaClient createLambdaClient(String accessKey, String secretKey, String region) {
                return LambdaClient.builder().region(validateRegion(region)).credentialsProvider(credentials(accessKey, secretKey)).build();
        }

        @Override
        public CostExplorerClient createCostExplorerClient(String accessKey, String secretKey) {
                        // COST EXPLORER TEMPORARILY DISABLED
                        // CLOUDCOSTAI USES CUR 2.0 + S3 FOR BILLING DATA
                        // DO NOT CALL AWS COST EXPLORER FROM DASHBOARD/BILLING FLOW
                        throw new IllegalStateException("AWS Cost Explorer is permanently disabled for CloudCostAI billing.");
        }

        @Override
        public BudgetsClient createBudgetsClient(String accessKey, String secretKey) {
                return BudgetsClient.builder().region(Region.US_EAST_1).credentialsProvider(credentials(accessKey, secretKey)).build();
        }

        private StaticCredentialsProvider credentials(String accessKey, String secretKey) {
                return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        }

        private Region validateRegion(String region) {
                if (region == null || Region.regions().stream().noneMatch(availableRegion ->
                                availableRegion.id().equals(region.toLowerCase(Locale.ROOT)))) {
                        throw new IllegalArgumentException("Unsupported AWS region");
                }
                return Region.of(region);
        }
}