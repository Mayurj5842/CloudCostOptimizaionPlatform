package com.cloudcostai.aws.service.impl;

import com.cloudcostai.aws.config.AwsClientFactory;
import com.cloudcostai.aws.dto.response.AwsResourceResponse;
import com.cloudcostai.aws.exception.AwsConnectionException;
import com.cloudcostai.aws.service.AwsConnectionSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeRegionsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeRegionsResponse;
import software.amazon.awssdk.services.ec2.model.DescribeSecurityGroupsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeVpcsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeVolumesRequest;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.InstanceState;
import software.amazon.awssdk.services.ec2.model.InstanceStateName;
import software.amazon.awssdk.services.ec2.model.Placement;
import software.amazon.awssdk.services.ec2.model.Reservation;
import software.amazon.awssdk.services.ec2.model.Volume;
import software.amazon.awssdk.services.ec2.model.VolumeState;
import software.amazon.awssdk.services.ec2.paginators.DescribeInstancesIterable;
import software.amazon.awssdk.services.ec2.paginators.DescribeSecurityGroupsIterable;
import software.amazon.awssdk.services.ec2.paginators.DescribeVolumesIterable;
import software.amazon.awssdk.services.ec2.paginators.DescribeVpcsIterable;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.ListFunctionsRequest;
import software.amazon.awssdk.services.lambda.paginators.ListFunctionsIterable;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.rds.model.DescribeDbInstancesRequest;
import software.amazon.awssdk.services.rds.paginators.DescribeDBInstancesIterable;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListBucketsRequest;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AwsResourceServiceImplTest {
    @Mock
    private AwsClientFactory clientFactory;

    @Mock
    private AwsConnectionSessionService sessionService;

    @Test
    void rejectsMissingConnectionBeforeCreatingAwsClients() {
        AwsResourceServiceImpl service = new AwsResourceServiceImpl(clientFactory, sessionService);
        when(sessionService.getSession(null)).thenThrow(new AwsConnectionException("AWS connection is not available. Please connect an AWS account first."));

        assertThatThrownBy(() -> service.discoverResources(null))
                .isInstanceOf(AwsConnectionException.class)
                .hasMessageContaining("connection is not available");
        verifyNoInteractions(clientFactory);
    }

    @Test
    void discoversEc2AndEbsUsingConnectedAccountAndSelectedRegion() {
        String accountId = "123456789012";
        String region = "eu-west-1";
        when(sessionService.getSession("token")).thenReturn(new AwsConnectionSessionService.ConnectionSession(
            "access", "secret", region, accountId, Instant.now().plusSeconds(600)));
        Ec2Client ec2 = mock(Ec2Client.class);
        S3Client s3 = mock(S3Client.class);
        RdsClient rds = mock(RdsClient.class);
        LambdaClient lambda = mock(LambdaClient.class);
        when(clientFactory.createEc2Client("access", "secret", region)).thenReturn(ec2);
        when(clientFactory.createS3Client("access", "secret", region)).thenReturn(s3);
        when(clientFactory.createRdsClient("access", "secret", region)).thenReturn(rds);
        when(clientFactory.createLambdaClient("access", "secret", region)).thenReturn(lambda);

        DescribeInstancesIterable instancePages = mock(DescribeInstancesIterable.class);
        SdkIterable<Reservation> reservations = sdkIterable(List.of(Reservation.builder().instances(Instance.builder()
            .instanceId("i-discovered").state(InstanceState.builder().name(InstanceStateName.STOPPED).build())
            .placement(Placement.builder().availabilityZone("eu-west-1a").build()).build()).build()));
        when(instancePages.reservations()).thenReturn(reservations);
        when(ec2.describeInstancesPaginator(any(DescribeInstancesRequest.class))).thenReturn(instancePages);
        DescribeVolumesIterable volumePages = mock(DescribeVolumesIterable.class);
        SdkIterable<Volume> volumeItems = sdkIterable(List.of(Volume.builder().volumeId("vol-discovered")
            .state(VolumeState.AVAILABLE).availabilityZone("eu-west-1a").attachments(List.of()).build()));
        when(volumePages.volumes()).thenReturn(volumeItems);
        when(ec2.describeVolumesPaginator(any(DescribeVolumesRequest.class))).thenReturn(volumePages);
        DescribeVpcsIterable vpcPages = mock(DescribeVpcsIterable.class);
        SdkIterable<software.amazon.awssdk.services.ec2.model.Vpc> vpcs = sdkIterable(List.of());
        when(vpcPages.vpcs()).thenReturn(vpcs);
        when(ec2.describeVpcsPaginator(any(DescribeVpcsRequest.class))).thenReturn(vpcPages);
        DescribeSecurityGroupsIterable securityGroupPages = mock(DescribeSecurityGroupsIterable.class);
        SdkIterable<software.amazon.awssdk.services.ec2.model.SecurityGroup> securityGroups = sdkIterable(List.of());
        when(securityGroupPages.securityGroups()).thenReturn(securityGroups);
        when(ec2.describeSecurityGroupsPaginator(any(DescribeSecurityGroupsRequest.class))).thenReturn(securityGroupPages);
        when(s3.listBuckets(any(ListBucketsRequest.class))).thenThrow(software.amazon.awssdk.services.s3.model.S3Exception.builder()
            .message("Access denied")
            .statusCode(403)
            .build());
        DescribeDBInstancesIterable databasePages = mock(DescribeDBInstancesIterable.class);
        SdkIterable<software.amazon.awssdk.services.rds.model.DBInstance> databases = sdkIterable(List.of());
        when(databasePages.dbInstances()).thenReturn(databases);
        when(rds.describeDBInstancesPaginator(any(DescribeDbInstancesRequest.class))).thenReturn(databasePages);
        ListFunctionsIterable functionPages = mock(ListFunctionsIterable.class);
        SdkIterable<software.amazon.awssdk.services.lambda.model.FunctionConfiguration> functions = sdkIterable(List.of());
        when(functionPages.functions()).thenReturn(functions);
        when(lambda.listFunctionsPaginator(any(ListFunctionsRequest.class))).thenReturn(functionPages);

        var response = new AwsResourceServiceImpl(clientFactory, sessionService).discoverResources("token");

        assertThat(response.accountId()).isEqualTo(accountId);
        assertThat(response.region()).isEqualTo(region);
        assertThat(response.ec2Instances()).singleElement().satisfies(instance -> {
            assertThat(instance.instanceId()).isEqualTo("i-discovered");
            assertThat(instance.state()).isEqualTo("stopped");
            assertThat(instance.region()).isEqualTo(region);
        });
        assertThat(response.ebsVolumes()).singleElement().satisfies(volume -> {
            assertThat(volume.volumeId()).isEqualTo("vol-discovered");
            assertThat(volume.state()).isEqualTo("available");
            assertThat(volume.attachedInstanceId()).isNull();
        });
        verify(clientFactory).createEc2Client("access", "secret", region);
        verify(clientFactory).createS3Client("access", "secret", region);
        verify(clientFactory).createRdsClient("access", "secret", region);
        verify(clientFactory).createLambdaClient("access", "secret", region);
        verify(ec2).describeInstancesPaginator(any(DescribeInstancesRequest.class));
        verify(ec2).describeVolumesPaginator(any(DescribeVolumesRequest.class));
    }

    @Test
    void recommendationDiscoveryPaginatesCurRegionsAndPreservesEachResourceRegion() {
        String accountId = "123456789012";
        String connectedRegion = "ap-south-1";
        when(sessionService.getSession("token")).thenReturn(new AwsConnectionSessionService.ConnectionSession(
            "access", "secret", connectedRegion, accountId, Instant.now().plusSeconds(600)));
        Ec2Client regionClient = mock(Ec2Client.class);
        when(clientFactory.createEc2Client("access", "secret", connectedRegion)).thenReturn(regionClient);
        when(regionClient.describeRegions(any(DescribeRegionsRequest.class))).thenReturn(DescribeRegionsResponse.builder()
            .regions(software.amazon.awssdk.services.ec2.model.Region.builder().regionName("eu-west-1").build(),
                software.amazon.awssdk.services.ec2.model.Region.builder().regionName("us-east-1").build())
            .build());
        for (String region : List.of("eu-west-1", "us-east-1")) {
            Ec2Client ec2 = mock(Ec2Client.class);
            when(clientFactory.createEc2Client("access", "secret", region)).thenReturn(ec2);
            DescribeInstancesIterable instancePages = mock(DescribeInstancesIterable.class);
            Instance instance = Instance.builder().instanceId("i-" + region).state(
                InstanceState.builder().name(InstanceStateName.STOPPED).build()).placement(
                Placement.builder().availabilityZone(region + "a").build()).build();
            SdkIterable<Reservation> reservationItems = sdkIterable(List.of(Reservation.builder()
                .instances(instance).build()));
            when(instancePages.reservations()).thenReturn(reservationItems);
            when(ec2.describeInstancesPaginator(any(DescribeInstancesRequest.class))).thenReturn(instancePages);
            DescribeVolumesIterable volumePages = mock(DescribeVolumesIterable.class);
            SdkIterable<Volume> volumeItems = sdkIterable(List.of(Volume.builder().volumeId("vol-" + region)
                .state(VolumeState.AVAILABLE).availabilityZone(region + "a").build()));
            when(volumePages.volumes()).thenReturn(volumeItems);
            when(ec2.describeVolumesPaginator(any(DescribeVolumesRequest.class))).thenReturn(volumePages);
        }

        var response = new AwsResourceServiceImpl(clientFactory, sessionService)
            .discoverResourcesForRecommendations("token", java.util.Set.of("eu-west-1", "us-east-1"));

        assertThat(response.accountId()).isEqualTo(accountId);
        assertThat(response.region()).isEqualTo("MULTI_REGION");
        assertThat(response.ec2Instances()).extracting(AwsResourceResponse.Ec2InstanceDto::region)
            .containsExactly("eu-west-1", "us-east-1");
        assertThat(response.ec2Instances()).extracting(AwsResourceResponse.Ec2InstanceDto::instanceId)
            .containsExactly("i-eu-west-1", "i-us-east-1");
        assertThat(response.ebsVolumes()).extracting(AwsResourceResponse.EbsVolumeDto::region)
            .containsExactly("eu-west-1", "us-east-1");
        verify(clientFactory).createEc2Client("access", "secret", "eu-west-1");
        verify(clientFactory).createEc2Client("access", "secret", "us-east-1");
        verify(regionClient).describeRegions(any(DescribeRegionsRequest.class));
    }

    @Test
    void recommendationDiscoveryReportsMissingDescribeRegionsPermissionInsteadOfFallingBackToCur() {
        String accountId = "123456789012";
        String connectedRegion = "ap-south-1";
        when(sessionService.getSession("token")).thenReturn(new AwsConnectionSessionService.ConnectionSession(
            "access", "secret", connectedRegion, accountId, Instant.now().plusSeconds(600)));
        Ec2Client regionClient = mock(Ec2Client.class);
        when(clientFactory.createEc2Client("access", "secret", connectedRegion)).thenReturn(regionClient);
        when(regionClient.describeRegions(any(DescribeRegionsRequest.class))).thenThrow(
            software.amazon.awssdk.services.ec2.model.Ec2Exception.builder()
                .message("Access denied")
                .statusCode(403)
                .build());
        assertThatThrownBy(() -> new AwsResourceServiceImpl(clientFactory, sessionService)
            .discoverResourcesForRecommendations("token", java.util.Set.of("eu-west-1")))
            .isInstanceOf(AwsConnectionException.class)
            .hasMessageContaining("ec2:DescribeRegions");
        verify(clientFactory, org.mockito.Mockito.never())
            .createEc2Client("access", "secret", "eu-west-1");
    }

    @SuppressWarnings("unchecked")
    private <T> SdkIterable<T> sdkIterable(List<T> values) {
        SdkIterable<T> iterable = mock(SdkIterable.class);
        when(iterable.stream()).thenReturn(values.stream());
        return iterable;
    }
}
