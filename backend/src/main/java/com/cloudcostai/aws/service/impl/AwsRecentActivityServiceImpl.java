package com.cloudcostai.aws.service.impl;

import com.cloudcostai.aws.config.AwsClientFactory;
import com.cloudcostai.aws.dto.response.AwsRecentActivityResponse;
import com.cloudcostai.aws.service.AwsConnectionSessionService;
import com.cloudcostai.aws.service.AwsRecentActivityService;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.InstanceStateName;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.ListFunctionsRequest;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.rds.model.DescribeDbInstancesRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListBucketsRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class AwsRecentActivityServiceImpl implements AwsRecentActivityService {
    private final AwsConnectionSessionService sessionService;
    private final AwsClientFactory clientFactory;

    public AwsRecentActivityServiceImpl(AwsConnectionSessionService sessionService, AwsClientFactory clientFactory) {
        this.sessionService = sessionService;
        this.clientFactory = clientFactory;
    }

    @Override
    public AwsRecentActivityResponse getRecentActivity(String connectionToken, int limit) {
        var session = sessionService.getSession(connectionToken);

        List<AwsRecentActivityResponse.Activity> activities = new ArrayList<>();

        try (Ec2Client ec2 = clientFactory.createEc2Client(session.accessKey(), session.secretKey(), session.region());
             S3Client s3 = clientFactory.createS3Client(session.accessKey(), session.secretKey(), session.region());
             RdsClient rds = clientFactory.createRdsClient(session.accessKey(), session.secretKey(), session.region());
             LambdaClient lambda = clientFactory.createLambdaClient(session.accessKey(), session.secretKey(), session.region())) {

            ec2.describeInstancesPaginator(DescribeInstancesRequest.builder().build())
                    .reservations().stream()
                    .flatMap(reservation -> reservation.instances().stream())
                    .filter(instance -> instance.launchTime() != null)
                    .map(instance -> new AwsRecentActivityResponse.Activity(
                            "ec2-" + instance.instanceId(),
                            "EC2",
                            "INSTANCE_LAUNCHED",
                            formatEc2State(instance),
                            instance.instanceId(),
                            instance.placement() == null || instance.placement().availabilityZone() == null
                                    ? session.region()
                                    : instance.placement().availabilityZone(),
                            instance.launchTime().toString().equals("") ? Instant.now() : instance.launchTime()
                    ))
                    .forEach(activities::add);

            s3.listBuckets(ListBucketsRequest.builder().build()).buckets().stream()
                    .filter(bucket -> bucket.creationDate() != null)
                    .map(bucket -> new AwsRecentActivityResponse.Activity(
                            "s3-" + bucket.name(),
                            "S3",
                            "BUCKET_CREATED",
                            "S3 bucket created",
                            bucket.name(),
                            session.region(),
                            bucket.creationDate()
                    ))
                    .forEach(activities::add);

            rds.describeDBInstancesPaginator(DescribeDbInstancesRequest.builder().build())
                    .dbInstances().stream()
                    .filter(db -> db.instanceCreateTime() != null)
                    .map(db -> new AwsRecentActivityResponse.Activity(
                            "rds-" + db.dbInstanceIdentifier(),
                            "RDS",
                            "DB_INSTANCE_CREATED",
                            "RDS database created",
                            db.dbInstanceIdentifier(),
                            db.availabilityZone() == null ? session.region() : db.availabilityZone(),
                            db.instanceCreateTime()
                    ))
                    .forEach(activities::add);

            lambda.listFunctionsPaginator(ListFunctionsRequest.builder().build())
                    .functions().stream()
                    .filter(function -> function.lastModified() != null)
                    .map(function -> new AwsRecentActivityResponse.Activity(
                            "lambda-" + function.functionName(),
                            "Lambda",
                            "FUNCTION_UPDATED",
                            "Lambda function updated",
                            function.functionName(),
                            function.toString().contains("arn") ? session.region() : session.region(),
                            parseLambdaTimestamp(function.lastModified())
                    ))
                    .forEach(activities::add);

        } catch (RuntimeException ex) {
            throw new IllegalStateException("Unable to load recent cloud activity for the connected AWS account.", ex);
        }

        return new AwsRecentActivityResponse(
                activities.stream()
                        .filter(Objects::nonNull)
                        .filter(activity -> activity.timestamp() != null)
                        .sorted(Comparator.comparing(AwsRecentActivityResponse.Activity::timestamp).reversed())
                        .limit(Math.max(1, limit))
                        .toList()
        );
    }

    private String formatEc2State(software.amazon.awssdk.services.ec2.model.Instance instance) {
        String state = instance.state() == null ? null : instance.state().nameAsString();
        if (state == null) {
            return "EC2 instance discovered";
        }
        String normalized = state.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "running" -> "EC2 instance started";
            case "stopped" -> "EC2 instance stopped";
            case "terminated" -> "EC2 instance terminated";
            default -> "EC2 instance updated";
        };
    }

        private Instant parseLambdaTimestamp(String value) {
                try {
                        return Instant.parse(value);
                } catch (RuntimeException exception) {
                        try {
                                return java.time.OffsetDateTime.parse(value, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")).toInstant();
                        } catch (RuntimeException ignored) {
                                return null;
                        }
                }
        }
}
