package com.cloudcostai.aws.service.impl;

import com.cloudcostai.aws.config.AwsClientFactory;
import com.cloudcostai.aws.dto.response.AwsResourceResponse;
import com.cloudcostai.aws.dto.response.AwsResourceResponse.AwsResourceSummary;
import com.cloudcostai.aws.exception.AwsConnectionException;
import com.cloudcostai.aws.service.AwsConnectionSessionService;
import com.cloudcostai.aws.service.AwsResourceService;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeSecurityGroupsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeVolumesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeVpcsRequest;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.Volume;
import software.amazon.awssdk.services.ec2.model.Vpc;
import software.amazon.awssdk.services.ec2.model.SecurityGroup;
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

@Service
public class AwsResourceServiceImpl implements AwsResourceService {
    private final AwsClientFactory clientFactory;
    private final AwsConnectionSessionService sessionService;

    public AwsResourceServiceImpl(AwsClientFactory clientFactory, AwsConnectionSessionService sessionService) {
        this.clientFactory = clientFactory;
        this.sessionService = sessionService;
    }

    @Override
    public AwsResourceResponse discoverResources(String connectionToken) {
        AwsConnectionSessionService.ConnectionSession session = sessionService.getSession(connectionToken);
        try (Ec2Client ec2 = clientFactory.createEc2Client(session.accessKey(), session.secretKey(), session.region());
             S3Client s3 = clientFactory.createS3Client(session.accessKey(), session.secretKey(), session.region());
             RdsClient rds = clientFactory.createRdsClient(session.accessKey(), session.secretKey(), session.region());
             LambdaClient lambda = clientFactory.createLambdaClient(session.accessKey(), session.secretKey(), session.region())) {

            List<AwsResourceResponse.Ec2InstanceDto> instances = ec2.describeInstancesPaginator(DescribeInstancesRequest.builder().build())
                    .reservations().stream().flatMap(reservation -> reservation.instances().stream()).map(instance -> toInstance(instance, session.region())).toList();
            List<AwsResourceResponse.EbsVolumeDto> volumes = ec2.describeVolumesPaginator(DescribeVolumesRequest.builder().build())
                    .volumes().stream().map(this::toVolume).toList();
            List<AwsResourceResponse.VpcDto> vpcs = ec2.describeVpcsPaginator(DescribeVpcsRequest.builder().build())
                    .vpcs().stream().map(vpc -> toVpc(vpc, session.region())).toList();
            List<AwsResourceResponse.SecurityGroupDto> securityGroups = ec2.describeSecurityGroupsPaginator(DescribeSecurityGroupsRequest.builder().build())
                    .securityGroups().stream().map(this::toSecurityGroup).toList();
            List<AwsResourceResponse.S3BucketDto> buckets = s3.listBuckets(ListBucketsRequest.builder().build()).buckets().stream()
                    .map(bucket -> toBucket(s3, bucket)).toList();
            List<AwsResourceResponse.RdsDatabaseDto> databases = rds.describeDBInstancesPaginator(DescribeDbInstancesRequest.builder().build())
                    .dbInstances().stream().map(database -> toDatabase(database, session.region())).toList();
            List<AwsResourceResponse.LambdaFunctionDto> functions = lambda.listFunctionsPaginator(ListFunctionsRequest.builder().build())
                    .functions().stream().map(function -> toFunction(function, session.region())).toList();

            int running = (int) instances.stream().filter(instance -> "running".equalsIgnoreCase(instance.state())).count();
            int stopped = (int) instances.stream().filter(instance -> "stopped".equalsIgnoreCase(instance.state())).count();
            int attached = (int) volumes.stream().filter(volume -> volume.attachedInstanceId() != null).count();
            int availableDatabases = (int) databases.stream().filter(database -> "available".equalsIgnoreCase(database.status())).count();
            int stoppedDatabases = (int) databases.stream().filter(database -> "stopped".equalsIgnoreCase(database.status())).count();
            int activeResources = running + availableDatabases + functions.size() + buckets.size();
            int total = instances.size() + volumes.size() + buckets.size() + databases.size() + functions.size() + vpcs.size() + securityGroups.size();
            AwsResourceSummary summary = new AwsResourceSummary(total, activeResources, instances.size(), running, stopped, volumes.size(), attached,
                    volumes.size() - attached, buckets.size(), databases.size(), availableDatabases, stoppedDatabases,
                    functions.size(), vpcs.size(), securityGroups.size());
            return new AwsResourceResponse(session.accountId(), session.region(), summary, instances, volumes, buckets, databases, functions, vpcs, securityGroups);
        } catch (RuntimeException exception) {
            throw new AwsConnectionException("Unable to fetch AWS resources. Please verify AWS permissions and connection.", exception);
        }
    }

    private AwsResourceResponse.Ec2InstanceDto toInstance(Instance instance, String region) {
        String name = instance.tags().stream().filter(tag -> "Name".equals(tag.key())).map(tag -> tag.value()).findFirst().orElse("");
        return new AwsResourceResponse.Ec2InstanceDto(instance.instanceId(), name, instance.instanceTypeAsString(), instance.state().nameAsString(),
                instance.placement().availabilityZone(), region, instance.privateIpAddress(), instance.publicIpAddress(), instant(instance.launchTime()));
    }

    private AwsResourceResponse.EbsVolumeDto toVolume(Volume volume) {
        String attachedInstance = volume.attachments().stream().map(attachment -> attachment.instanceId()).filter(Objects::nonNull).findFirst().orElse(null);
        return new AwsResourceResponse.EbsVolumeDto(volume.volumeId(), volume.volumeTypeAsString(), volume.size(), volume.stateAsString(), volume.availabilityZone(), attachedInstance);
    }

    private AwsResourceResponse.S3BucketDto toBucket(S3Client s3, Bucket bucket) {
        String region = "us-east-1";
        try {
            String location = s3.getBucketLocation(GetBucketLocationRequest.builder().bucket(bucket.name()).build()).locationConstraintAsString();
            if (location != null && !location.isBlank()) region = "EU".equals(location) ? "eu-west-1" : location;
        } catch (S3Exception exception) {
            throw exception;
        }
        return new AwsResourceResponse.S3BucketDto(bucket.name(), instant(bucket.creationDate()), region);
    }

    private AwsResourceResponse.RdsDatabaseDto toDatabase(DBInstance database, String region) {
        String endpoint = database.endpoint() == null ? null : database.endpoint().address();
        return new AwsResourceResponse.RdsDatabaseDto(database.dbInstanceIdentifier(), database.engine(), database.engineVersion(),
                database.dbInstanceClass(), database.dbInstanceStatus(), database.availabilityZone(), endpoint, region);
    }

    private AwsResourceResponse.LambdaFunctionDto toFunction(FunctionConfiguration function, String region) {
        return new AwsResourceResponse.LambdaFunctionDto(function.functionName(), function.runtimeAsString(), function.memorySize(),
                function.timeout(), function.lastModified(), function.functionArn(), region);
    }

    private AwsResourceResponse.VpcDto toVpc(Vpc vpc, String region) {
        return new AwsResourceResponse.VpcDto(vpc.vpcId(), vpc.cidrBlock(), vpc.stateAsString(), vpc.isDefault(), region);
    }

    private AwsResourceResponse.SecurityGroupDto toSecurityGroup(SecurityGroup group) {
        return new AwsResourceResponse.SecurityGroupDto(group.groupId(), group.groupName(), group.description(), group.vpcId());
    }

    private String instant(java.time.Instant value) {
        return value == null ? null : value.toString();
    }
}
