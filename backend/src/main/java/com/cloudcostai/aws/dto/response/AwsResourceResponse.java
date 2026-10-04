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
        List<SecurityGroupDto> securityGroups,
        List<RecentResourceDto> recentResources
) {
    public AwsResourceResponse(String accountId, String region, AwsResourceSummary summary,
                               List<Ec2InstanceDto> ec2Instances, List<EbsVolumeDto> ebsVolumes,
                               List<S3BucketDto> s3Buckets, List<RdsDatabaseDto> rdsDatabases,
                               List<LambdaFunctionDto> lambdaFunctions, List<VpcDto> vpcs,
                               List<SecurityGroupDto> securityGroups) {
        this(accountId, region, summary, ec2Instances, ebsVolumes, s3Buckets, rdsDatabases,
                lambdaFunctions, vpcs, securityGroups, List.of());
    }

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
            int securityGroups,
            int healthyResources,
            int unusedVolumes
    ) {
        public AwsResourceSummary(int totalResources, int activeResources, int ec2Instances, int runningEc2Instances,
                                  int stoppedEc2Instances, int ebsVolumes, int attachedEbsVolumes, int unattachedEbsVolumes,
                                  int s3Buckets, int rdsDatabases, int availableRdsDatabases, int stoppedRdsDatabases,
                                  int lambdaFunctions, int vpcs, int securityGroups) {
            this(totalResources, activeResources, ec2Instances, runningEc2Instances, stoppedEc2Instances, ebsVolumes,
                    attachedEbsVolumes, unattachedEbsVolumes, s3Buckets, rdsDatabases, availableRdsDatabases,
                    stoppedRdsDatabases, lambdaFunctions, vpcs, securityGroups, 0, unattachedEbsVolumes);
        }
    }

    public record Ec2InstanceDto(String instanceId, String name, String instanceType, String state,
                                 String availabilityZone, String region, String privateIp, String publicIp,
                                                                 String launchTime, List<TagDto> tags, String platform) {
                public Ec2InstanceDto(String instanceId, String name, String instanceType, String state,
                                                          String availabilityZone, String region, String privateIp, String publicIp,
                                                          String launchTime) {
                        this(instanceId, name, instanceType, state, availabilityZone, region, privateIp, publicIp, launchTime, List.of(), null);
                }
        }

        public record EbsVolumeDto(String volumeId, String volumeType, Integer size, String state,
                                   String availabilityZone, String attachedInstanceId, String creationTime,
                                   Boolean encrypted, String region, List<TagDto> tags) {
                        public EbsVolumeDto(String volumeId, String volumeType, Integer size, String state,
                                            String availabilityZone, String attachedInstanceId, String creationTime,
                                            Boolean encrypted, String region) {
                                this(volumeId, volumeType, size, state, availabilityZone, attachedInstanceId,
                                        creationTime, encrypted, region, List.of());
                        }
                        public EbsVolumeDto(String volumeId, String volumeType, Integer size, String state,
                                                                  String availabilityZone, String attachedInstanceId, String creationTime,
                                                                  Boolean encrypted) {
                                this(volumeId, volumeType, size, state, availabilityZone, attachedInstanceId,
                                        creationTime, encrypted, null, List.of());
                        }
                public EbsVolumeDto(String volumeId, String volumeType, int size, String state,
                                                        String availabilityZone, String attachedInstanceId) {
                                this(volumeId, volumeType, size, state, availabilityZone, attachedInstanceId,
                                        null, null, null, List.of());
                }
        }

    public record S3BucketDto(String bucketName, String creationDate, String region) {}

    public record RdsDatabaseDto(String identifier, String engine, String engineVersion,
                                 String instanceClass, String status, String availabilityZone,
                                                                 String endpoint, String region, Integer allocatedStorage,
                                                                 String creationTime, Boolean multiAz) {
                public RdsDatabaseDto(String identifier, String engine, String engineVersion,
                                                          String instanceClass, String status, String availabilityZone,
                                                          String endpoint, String region) {
                        this(identifier, engine, engineVersion, instanceClass, status, availabilityZone, endpoint, region, null, null, null);
                }
        }

    public record LambdaFunctionDto(String functionName, String runtime, Integer memorySize,
                                    Integer timeout, String lastModified, String functionArn,
                                                                        String region, String handler, String state, String runtimeStatus) {
                public LambdaFunctionDto(String functionName, String runtime, Integer memorySize,
                                                                 Integer timeout, String lastModified, String functionArn,
                                                                 String region) {
                        this(functionName, runtime, memorySize, timeout, lastModified, functionArn, region, null, null, null);
                }
        }

    public record VpcDto(String vpcId, String cidrBlock, String state, boolean defaultVpc,
                                                 String region, String name) {
                public VpcDto(String vpcId, String cidrBlock, String state, boolean defaultVpc,
                                          String region) {
                        this(vpcId, cidrBlock, state, defaultVpc, region, null);
                }
        }

    public record SecurityGroupDto(String groupId, String groupName, String description,
                                                                   String vpcId, String region, Integer inboundRuleCount,
                                                                   Integer outboundRuleCount) {
                public SecurityGroupDto(String groupId, String groupName, String description,
                                                                String vpcId) {
                        this(groupId, groupName, description, vpcId, null, null, null);
                }
        }

        public record RecentResourceDto(String resourceType, String resourceId, String name, String timestamp, String status) {}

        public record TagDto(String key, String value) {}
}
