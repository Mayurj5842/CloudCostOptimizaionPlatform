package com.cloudcostai.aws.service.impl;

import com.cloudcostai.aws.config.AwsClientFactory;
import com.cloudcostai.aws.dto.response.AwsResourceResponse;
import com.cloudcostai.aws.dto.response.AwsResourceResponse.AwsResourceSummary;
import com.cloudcostai.aws.exception.AwsConnectionException;
import com.cloudcostai.aws.service.AwsConnectionSessionService;
import com.cloudcostai.aws.service.AwsResourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeRegionsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeSecurityGroupsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeVolumesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeVpcsRequest;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.Volume;
import software.amazon.awssdk.services.ec2.model.Vpc;
import software.amazon.awssdk.services.ec2.model.SecurityGroup;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.ListFunctionsRequest;
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.rds.model.DescribeDbInstancesRequest;
import software.amazon.awssdk.services.rds.model.DBInstance;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.GetBucketLocationRequest;
import software.amazon.awssdk.services.s3.model.ListBucketsRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.List;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import java.util.function.Supplier;

@Service
public class AwsResourceServiceImpl implements AwsResourceService {
    private static final Logger logger = LoggerFactory.getLogger(AwsResourceServiceImpl.class);
    private final AwsClientFactory clientFactory;
    private final AwsConnectionSessionService sessionService;

    public AwsResourceServiceImpl(AwsClientFactory clientFactory, AwsConnectionSessionService sessionService) {
        this.clientFactory = clientFactory;
        this.sessionService = sessionService;
    }

    @Override
    public AwsResourceResponse discoverResources(String connectionToken) {
        AwsConnectionSessionService.ConnectionSession session = sessionService.getSession(connectionToken);
        logger.info("AWS EC2/EBS discovery started: region={}", session.region());
        try (Ec2Client ec2 = clientFactory.createEc2Client(session.accessKey(), session.secretKey(), session.region());
             S3Client s3 = clientFactory.createS3Client(session.accessKey(), session.secretKey(), session.region());
             RdsClient rds = clientFactory.createRdsClient(session.accessKey(), session.secretKey(), session.region());
             LambdaClient lambda = clientFactory.createLambdaClient(session.accessKey(), session.secretKey(), session.region())) {

            List<AwsResourceResponse.Ec2InstanceDto> instances = ec2.describeInstancesPaginator(DescribeInstancesRequest.builder().build())
                    .reservations().stream().flatMap(reservation -> reservation.instances().stream()).map(instance -> toInstance(instance, session.region())).toList();
            List<AwsResourceResponse.EbsVolumeDto> volumes = ec2.describeVolumesPaginator(DescribeVolumesRequest.builder().build())
                    .volumes().stream().map(volume -> toVolume(volume, session.region())).toList();
                logger.info("AWS EC2/EBS discovery completed: region={}, ec2Resources={}, ebsResources={}",
                    session.region(), instances.size(), volumes.size());
                List<AwsResourceResponse.VpcDto> vpcs = optionalResources("VPC", () -> ec2.describeVpcsPaginator(DescribeVpcsRequest.builder().build())
                    .vpcs().stream().map(vpc -> toVpc(vpc, session.region())).toList());
                List<AwsResourceResponse.SecurityGroupDto> securityGroups = optionalResources("security group", () -> ec2.describeSecurityGroupsPaginator(DescribeSecurityGroupsRequest.builder().build())
                    .securityGroups().stream().map(group -> toSecurityGroup(group, session.region())).toList());
                List<AwsResourceResponse.S3BucketDto> buckets = optionalResources("S3 bucket", () -> s3.listBuckets(ListBucketsRequest.builder().build()).buckets().stream()
                    .map(bucket -> toBucket(s3, bucket)).toList());
                List<AwsResourceResponse.RdsDatabaseDto> databases = optionalResources("RDS database", () -> rds.describeDBInstancesPaginator(DescribeDbInstancesRequest.builder().build())
                    .dbInstances().stream().map(database -> toDatabase(database, session.region())).toList());
                List<AwsResourceResponse.LambdaFunctionDto> functions = optionalResources("Lambda function", () -> lambda.listFunctionsPaginator(ListFunctionsRequest.builder().build())
                    .functions().stream().map(function -> toFunction(function, session.region())).toList());

            int running = (int) instances.stream().filter(instance -> "running".equalsIgnoreCase(instance.state())).count();
            int stopped = (int) instances.stream().filter(instance -> "stopped".equalsIgnoreCase(instance.state())).count();
            int attached = (int) volumes.stream().filter(volume -> volume.attachedInstanceId() != null).count();
            int availableDatabases = (int) databases.stream().filter(database -> "available".equalsIgnoreCase(database.status())).count();
            int stoppedDatabases = (int) databases.stream().filter(database -> "stopped".equalsIgnoreCase(database.status())).count();
                int activeFunctions = (int) functions.stream().filter(function -> "Active".equalsIgnoreCase(function.state())).count();
                int availableVpcs = (int) vpcs.stream().filter(vpc -> "available".equalsIgnoreCase(vpc.state())).count();
                int healthy = running + availableDatabases + activeFunctions + availableVpcs;
                int activeResources = healthy + buckets.size() + attached;
            int total = instances.size() + volumes.size() + buckets.size() + databases.size() + functions.size() + vpcs.size() + securityGroups.size();
            AwsResourceSummary summary = new AwsResourceSummary(total, activeResources, instances.size(), running, stopped, volumes.size(), attached,
                    volumes.size() - attached, buckets.size(), databases.size(), availableDatabases, stoppedDatabases,
                    functions.size(), vpcs.size(), securityGroups.size(), healthy, volumes.size() - attached);
                return new AwsResourceResponse(session.accountId(), session.region(), summary, instances, volumes, buckets, databases, functions, vpcs, securityGroups,
                    recentResources(instances, volumes, buckets, databases, functions));
        } catch (RuntimeException exception) {
            logger.warn("AWS EC2/EBS discovery failed: region={}, exceptionType={}",
                    session.region(), exception.getClass().getSimpleName());
            throw new AwsConnectionException("Unable to fetch AWS resources. Please verify AWS permissions and connection.", exception);
        }
    }

    @Override
    public AwsResourceResponse discoverResourcesForRecommendations(String connectionToken, Set<String> regions) {
        AwsConnectionSessionService.ConnectionSession session = sessionService.getSession(connectionToken);
        List<String> targetRegions;
        try (Ec2Client ec2 = clientFactory.createEc2Client(session.accessKey(), session.secretKey(), session.region())) {
            targetRegions = ec2.describeRegions(DescribeRegionsRequest.builder().build()).regions().stream()
                .map(software.amazon.awssdk.services.ec2.model.Region::regionName)
                .filter(region -> region != null && !region.isBlank())
                .distinct().sorted().toList();
        } catch (RuntimeException exception) {
            logger.warn("AWS enabled-region discovery failed: exceptionType={}", exception.getClass().getSimpleName());
            if (isAccessDenied(exception)) {
                throw new AwsConnectionException(
                    "AWS denied ec2:DescribeRegions. Grant this read permission to discover resources across enabled regions.",
                    exception);
            }
            throw new AwsConnectionException(
                "Unable to discover enabled AWS regions with ec2:DescribeRegions. Verify the connected AWS credentials and retry.",
                exception);
        }
        if (targetRegions.isEmpty()) {
            throw new AwsConnectionException(
                "ec2:DescribeRegions returned no enabled regions for the connected AWS account.");
        }

        List<AwsResourceResponse.Ec2InstanceDto> instances = new ArrayList<>();
        List<AwsResourceResponse.EbsVolumeDto> volumes = new ArrayList<>();
        for (String region : targetRegions) {
            logger.info("AWS recommendation EC2/EBS discovery started: region={}", region);
            try (Ec2Client ec2 = clientFactory.createEc2Client(session.accessKey(), session.secretKey(), region)) {
                try {
                    instances.addAll(ec2.describeInstancesPaginator(DescribeInstancesRequest.builder().build())
                        .reservations().stream().flatMap(reservation -> reservation.instances().stream())
                        .map(instance -> toInstance(instance, region)).toList());
                } catch (RuntimeException exception) {
                    throw discoveryFailure("ec2:DescribeInstances", region, exception);
                }
                try {
                    volumes.addAll(ec2.describeVolumesPaginator(DescribeVolumesRequest.builder().build())
                        .volumes().stream().map(volume -> toVolume(volume, region)).toList());
                } catch (RuntimeException exception) {
                    throw discoveryFailure("ec2:DescribeVolumes", region, exception);
                }
            } catch (AwsConnectionException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                logger.warn("AWS recommendation EC2/EBS discovery failed: region={}, exceptionType={}",
                    region, exception.getClass().getSimpleName());
                throw new AwsConnectionException("Unable to create an EC2 client for resource discovery in "
                    + region + ". Verify the connected AWS credentials.", exception);
            }
        }

        int running = (int) instances.stream().filter(instance -> "running".equalsIgnoreCase(instance.state())).count();
        int stopped = (int) instances.stream().filter(instance -> "stopped".equalsIgnoreCase(instance.state())).count();
        int attached = (int) volumes.stream().filter(volume -> volume.attachedInstanceId() != null).count();
        AwsResourceSummary summary = new AwsResourceSummary(instances.size() + volumes.size(), running + attached,
            instances.size(), running, stopped, volumes.size(), attached, volumes.size() - attached,
            0, 0, 0, 0, 0, 0, 0);
        logger.info("AWS recommendation EC2/EBS discovery completed: regionsScanned={}, regions={}, ec2Resources={}, ebsResources={}, billingRegions={}",
            targetRegions.size(), targetRegions, instances.size(), volumes.size(), regions);
        return new AwsResourceResponse(session.accountId(), "MULTI_REGION", summary, instances, volumes,
            List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private AwsConnectionException discoveryFailure(String operation, String region, RuntimeException cause) {
        if (isAccessDenied(cause)) {
            return new AwsConnectionException("AWS denied " + operation + " in " + region
                + ". Grant this read permission for the connected account.", cause);
        }
        return new AwsConnectionException("Unable to run " + operation + " in " + region
            + ". Verify the connected AWS credentials and retry.", cause);
    }

    private boolean isAccessDenied(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof AwsServiceException awsException) {
                String errorCode = awsException.awsErrorDetails() == null
                    ? "" : awsException.awsErrorDetails().errorCode();
                if (awsException.statusCode() == 401 || awsException.statusCode() == 403
                    || (errorCode != null && (errorCode.toLowerCase().contains("accessdenied")
                        || errorCode.toLowerCase().contains("unauthorized")
                        || errorCode.toLowerCase().contains("forbidden")))) {
                    return true;
                }
            }
        }
        return false;
    }

    private <T> List<T> optionalResources(String resourceType, Supplier<List<T>> discovery) {
        try {
            return discovery.get();
        } catch (RuntimeException exception) {
            logger.warn("Optional AWS resource discovery failed: resourceType={}, exceptionType={}",
                    resourceType, exception.getClass().getSimpleName());
            return List.of();
        }
    }

    private AwsResourceResponse.Ec2InstanceDto toInstance(Instance instance, String region) {
        String name = instance.tags().stream().filter(tag -> "Name".equals(tag.key())).map(Tag::value).findFirst().orElse("");
        return new AwsResourceResponse.Ec2InstanceDto(instance.instanceId(), name, instance.instanceTypeAsString(), instance.state() == null ? null : instance.state().nameAsString(),
            instance.placement() == null ? null : instance.placement().availabilityZone(), region, instance.privateIpAddress(), instance.publicIpAddress(), instant(instance.launchTime()),
            instance.tags().stream().map(tag -> new AwsResourceResponse.TagDto(tag.key(), tag.value())).toList(), instance.platformAsString());
    }

    private AwsResourceResponse.EbsVolumeDto toVolume(Volume volume, String region) {
        String attachedInstance = volume.attachments().stream().map(attachment -> attachment.instanceId()).filter(Objects::nonNull).findFirst().orElse(null);
        return new AwsResourceResponse.EbsVolumeDto(volume.volumeId(), volume.volumeTypeAsString(), volume.size(), volume.stateAsString(), volume.availabilityZone(), attachedInstance,
            instant(volume.createTime()), volume.encrypted(), region,
            volume.tags().stream().map(tag -> new AwsResourceResponse.TagDto(tag.key(), tag.value())).toList());
    }

    private AwsResourceResponse.S3BucketDto toBucket(S3Client s3, Bucket bucket) {
        String region = "us-east-1";
        try {
            String location = s3.getBucketLocation(GetBucketLocationRequest.builder().bucket(bucket.name()).build()).locationConstraintAsString();
            if (location != null && !location.isBlank()) region = "EU".equals(location) ? "eu-west-1" : location;
        } catch (S3Exception exception) {
            region = "Not available";
        }
        return new AwsResourceResponse.S3BucketDto(bucket.name(), instant(bucket.creationDate()), region);
    }

    private AwsResourceResponse.RdsDatabaseDto toDatabase(DBInstance database, String region) {
        String endpoint = database.endpoint() == null ? null : database.endpoint().address();
        return new AwsResourceResponse.RdsDatabaseDto(database.dbInstanceIdentifier(), database.engine(), database.engineVersion(),
            database.dbInstanceClass(), database.dbInstanceStatus(), database.availabilityZone(), endpoint, region,
            database.allocatedStorage(), instant(database.instanceCreateTime()), database.multiAZ());
    }

    private AwsResourceResponse.LambdaFunctionDto toFunction(FunctionConfiguration function, String region) {
        return new AwsResourceResponse.LambdaFunctionDto(function.functionName(), function.runtimeAsString(), function.memorySize(),
            function.timeout(), function.lastModified(), function.functionArn(), region, function.handler(), function.stateAsString(), function.lastUpdateStatusAsString());
    }

    private AwsResourceResponse.VpcDto toVpc(Vpc vpc, String region) {
        String name = vpc.tags().stream().filter(tag -> "Name".equals(tag.key())).map(Tag::value).findFirst().orElse(null);
        return new AwsResourceResponse.VpcDto(vpc.vpcId(), vpc.cidrBlock(), vpc.stateAsString(), vpc.isDefault(), region, name);
    }

    private AwsResourceResponse.SecurityGroupDto toSecurityGroup(SecurityGroup group, String region) {
        return new AwsResourceResponse.SecurityGroupDto(group.groupId(), group.groupName(), group.description(), group.vpcId(), region,
                group.ipPermissions().size(), group.ipPermissionsEgress().size());
    }

    private List<AwsResourceResponse.RecentResourceDto> recentResources(List<AwsResourceResponse.Ec2InstanceDto> instances,
                                                                         List<AwsResourceResponse.EbsVolumeDto> volumes,
                                                                         List<AwsResourceResponse.S3BucketDto> buckets,
                                                                         List<AwsResourceResponse.RdsDatabaseDto> databases,
                                                                         List<AwsResourceResponse.LambdaFunctionDto> functions) {
        List<AwsResourceResponse.RecentResourceDto> recent = new ArrayList<>();
        instances.stream().filter(item -> item.launchTime() != null).forEach(item -> recent.add(new AwsResourceResponse.RecentResourceDto("EC2", item.instanceId(), item.name(), item.launchTime(), item.state())));
        volumes.stream().filter(item -> item.creationTime() != null).forEach(item -> recent.add(new AwsResourceResponse.RecentResourceDto("EBS", item.volumeId(), item.volumeId(), item.creationTime(), item.state())));
        buckets.stream().filter(item -> item.creationDate() != null).forEach(item -> recent.add(new AwsResourceResponse.RecentResourceDto("S3", item.bucketName(), item.bucketName(), item.creationDate(), "Unknown")));
        databases.stream().filter(item -> item.creationTime() != null).forEach(item -> recent.add(new AwsResourceResponse.RecentResourceDto("RDS", item.identifier(), item.identifier(), item.creationTime(), item.status())));
        functions.stream().filter(item -> item.lastModified() != null).forEach(item -> recent.add(new AwsResourceResponse.RecentResourceDto("Lambda", item.functionName(), item.functionName(), item.lastModified(), item.state())));
        return recent.stream().sorted(Comparator.comparing(AwsResourceResponse.RecentResourceDto::timestamp).reversed()).limit(20).toList();
    }

    private String instant(java.time.Instant value) {
        return value == null ? null : value.toString();
    }
}
