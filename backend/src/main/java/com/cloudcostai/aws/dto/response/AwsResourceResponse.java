package com.cloudcostai.aws.dto.response;

import java.util.List;

public record AwsResourceResponse(
        String accountId,
        String region,
        AwsResourceSummary summary,
        List<Ec2InstanceDto> ec2Instances,
        List<EbsVolumeDto> ebsVolumes,
        List<S3BucketDto> s3Buckets,
        List<RdsDatabaseDto> rdsDatabases,
        List<LambdaFunctionDto> lambdaFunctions,
        List<VpcDto> vpcs,
        List<SecurityGroupDto> securityGroups
) {
    public record AwsResourceSummary(
            int totalResources,
            int activeResources,
            int ec2Instances,
            int runningEc2Instances,
            int stoppedEc2Instances,
            int ebsVolumes,
            int attachedEbsVolumes,
            int unattachedEbsVolumes,
            int s3Buckets,
            int rdsDatabases,
            int availableRdsDatabases,
            int stoppedRdsDatabases,
            int lambdaFunctions,
            int vpcs,
            int securityGroups
    ) {}

    public record Ec2InstanceDto(String instanceId, String name, String instanceType, String state,
                                 String availabilityZone, String region, String privateIp, String publicIp,
                                 String launchTime) {}

    public record EbsVolumeDto(String volumeId, String volumeType, int size, String state,
                               String availabilityZone, String attachedInstanceId) {}

    public record S3BucketDto(String bucketName, String creationDate, String region) {}

    public record RdsDatabaseDto(String identifier, String engine, String engineVersion,
                                 String instanceClass, String status, String availabilityZone,
                                 String endpoint, String region) {}

    public record LambdaFunctionDto(String functionName, String runtime, Integer memorySize,
                                    Integer timeout, String lastModified, String functionArn,
                                    String region) {}

    public record VpcDto(String vpcId, String cidrBlock, String state, boolean defaultVpc,
                         String region) {}

    public record SecurityGroupDto(String groupId, String groupName, String description,
                                   String vpcId) {}
}
