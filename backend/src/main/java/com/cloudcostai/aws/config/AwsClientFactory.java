package com.cloudcostai.aws.config;

import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.budgets.BudgetsClient;

public interface AwsClientFactory {

    StsClient createStsClient(String accessKey, String secretKey, String region);

    Ec2Client createEc2Client(String accessKey, String secretKey, String region);

    S3Client createS3Client(String accessKey, String secretKey, String region);

    RdsClient createRdsClient(String accessKey, String secretKey, String region);

    LambdaClient createLambdaClient(String accessKey, String secretKey, String region);

    CostExplorerClient createCostExplorerClient(String accessKey, String secretKey);

    BudgetsClient createBudgetsClient(String accessKey, String secretKey);
}