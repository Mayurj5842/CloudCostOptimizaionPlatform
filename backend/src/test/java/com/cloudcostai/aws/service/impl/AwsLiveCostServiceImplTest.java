package com.cloudcostai.aws.service.impl;

import com.cloudcostai.aws.config.AwsClientFactory;
import com.cloudcostai.aws.model.AwsBilling;
import com.cloudcostai.aws.repository.AwsBillingRepository;
import com.cloudcostai.aws.service.AwsConnectionSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.costexplorer.model.DateInterval;
import software.amazon.awssdk.services.costexplorer.model.Group;
import software.amazon.awssdk.services.costexplorer.model.MetricValue;
import software.amazon.awssdk.services.costexplorer.model.ResultByTime;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageResponse;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.tuple;

@ExtendWith(MockitoExtension.class)
class AwsLiveCostServiceImplTest {
    @Mock private AwsClientFactory clientFactory;
    @Mock private AwsConnectionSessionService sessionService;
    @Mock private AwsBillingRepository billingRepository;
    @Mock private CostExplorerClient costExplorerClient;

    @Test
    void eachFetchCallsCostExplorerAndReturnsServiceTotals() {
        when(sessionService.getSession("token")).thenReturn(new AwsConnectionSessionService.ConnectionSession(
                "access", "secret", "us-east-1", "123456789012", Instant.now().plusSeconds(300)));
        when(clientFactory.createCostExplorerClient("access", "secret")).thenReturn(costExplorerClient);
        when(costExplorerClient.getCostAndUsage(any(GetCostAndUsageRequest.class))).thenReturn(response());
        when(billingRepository.findByRecordKey(any())).thenReturn(Optional.empty());

        AwsLiveCostServiceImpl service = new AwsLiveCostServiceImpl(clientFactory, sessionService, billingRepository);

        var first = service.fetch("token", "2026-08");
        var second = service.fetch("token", "2026-08");

        assertThat(first.services()).extracting("service", "cost")
                .containsExactly(tuple("Amazon EC2", new java.math.BigDecimal("12.50")),
                        tuple("Amazon S3", new java.math.BigDecimal("2.50")));
        assertThat(second.cost()).isEqualByComparingTo("15.00");
        var requestCaptor = org.mockito.ArgumentCaptor.forClass(GetCostAndUsageRequest.class);
        verify(costExplorerClient, times(2)).getCostAndUsage(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues().get(0).groupBy())
                .extracting(group -> group.type().toString(), group -> group.key())
                .containsExactly(tuple("DIMENSION", "REGION"), tuple("DIMENSION", "SERVICE"));
        verify(costExplorerClient, times(2)).close();
    }

        @Test
        void fetchPersistsCostExplorerRowsForTheConnectedAccountAndSelectedMonth() {
                String accountId = "123456789012";
                when(sessionService.getSession("token")).thenReturn(new AwsConnectionSessionService.ConnectionSession(
                                "access", "secret", "us-east-1", accountId, Instant.now().plusSeconds(300)));
                when(clientFactory.createCostExplorerClient("access", "secret")).thenReturn(costExplorerClient);
                when(costExplorerClient.getCostAndUsage(any(GetCostAndUsageRequest.class))).thenReturn(response());
                when(billingRepository.findByRecordKey(any())).thenReturn(Optional.empty());

                new AwsLiveCostServiceImpl(clientFactory, sessionService, billingRepository).fetch("token", "2026-08");

                var savedRows = org.mockito.ArgumentCaptor.forClass(AwsBilling.class);
                verify(billingRepository, times(2)).save(savedRows.capture());
                assertThat(savedRows.getAllValues()).extracting(AwsBilling::getAwsAccountId, AwsBilling::getBillingPeriodStart,
                                                AwsBilling::getCostAmount, AwsBilling::getDataSource)
                                .containsExactly(tuple(accountId, java.time.LocalDate.of(2026, 8, 1), new java.math.BigDecimal("12.50"), "COST_EXPLORER"),
                                                tuple(accountId, java.time.LocalDate.of(2026, 8, 1), new java.math.BigDecimal("2.50"), "COST_EXPLORER"));
        }

        @Test
        void persistsGroupedCostsEvenWhenCostExplorerOmitsDailyTotal() {
                when(sessionService.getSession("token")).thenReturn(new AwsConnectionSessionService.ConnectionSession(
                                                "access", "secret", "us-east-1", "123456789012", Instant.now().plusSeconds(300)));
                when(clientFactory.createCostExplorerClient("access", "secret")).thenReturn(costExplorerClient);
                when(costExplorerClient.getCostAndUsage(any(GetCostAndUsageRequest.class))).thenReturn(responseWithoutTotal());
                when(billingRepository.findByRecordKey(any())).thenReturn(Optional.empty());

                var result = new AwsLiveCostServiceImpl(clientFactory, sessionService, billingRepository)
                                                .fetch("token", "2026-08");

                assertThat(result.status()).isEqualTo("SUCCESS");
                assertThat(result.cost()).isEqualByComparingTo("15.00");
                verify(billingRepository, times(2)).save(any(AwsBilling.class));
        }

        @Test
        void usesBoundedStableRecordKeysForLongServiceNames() {
                String longServiceName = "Amazon " + "Example Service ".repeat(12);
                when(sessionService.getSession("token")).thenReturn(new AwsConnectionSessionService.ConnectionSession(
                                                "access", "secret", "us-east-1", "123456789012", Instant.now().plusSeconds(300)));
                when(clientFactory.createCostExplorerClient("access", "secret")).thenReturn(costExplorerClient);
                when(costExplorerClient.getCostAndUsage(any(GetCostAndUsageRequest.class)))
                                                .thenReturn(responseForService(longServiceName));
                when(billingRepository.findByRecordKey(any())).thenReturn(Optional.empty());

                new AwsLiveCostServiceImpl(clientFactory, sessionService, billingRepository).fetch("token", "2026-08");

                var savedRow = org.mockito.ArgumentCaptor.forClass(AwsBilling.class);
                verify(billingRepository).save(savedRow.capture());
                assertThat(savedRow.getValue().getRecordKey()).hasSizeLessThanOrEqualTo(128);
                assertThat(savedRow.getValue().getServiceName()).isEqualTo(longServiceName);
                assertThat(savedRow.getValue().getDataSource()).isEqualTo("COST_EXPLORER");
        }

                    @Test
                    void repeatedFetchUpdatesExistingRecordKeysInsteadOfInsertingDuplicates() {
                        String accountId = "123456789012";
                        var date = java.time.LocalDate.of(2026, 8, 1);
                        AwsBilling existingEc2 = billingRow(accountId, date, "Amazon EC2", "us-east-1", "1.00");
                        AwsBilling existingS3 = billingRow(accountId, date, "Amazon S3", "us-east-1", "1.00");
                        when(sessionService.getSession("token")).thenReturn(new AwsConnectionSessionService.ConnectionSession(
                                "access", "secret", "us-east-1", accountId, Instant.now().plusSeconds(300)));
                        when(clientFactory.createCostExplorerClient("access", "secret")).thenReturn(costExplorerClient);
                        when(costExplorerClient.getCostAndUsage(any(GetCostAndUsageRequest.class))).thenReturn(response());
                        when(billingRepository.findByRecordKey("COST_EXPLORER|" + accountId + "|2026-08-01|us-east-1|Amazon EC2"))
                                .thenReturn(Optional.of(existingEc2));
                        when(billingRepository.findByRecordKey("COST_EXPLORER|" + accountId + "|2026-08-01|us-east-1|Amazon S3"))
                                .thenReturn(Optional.of(existingS3));

                        new AwsLiveCostServiceImpl(clientFactory, sessionService, billingRepository).fetch("token", "2026-08");

                        assertThat(existingEc2.getCostAmount()).isEqualByComparingTo("12.50");
                        assertThat(existingS3.getCostAmount()).isEqualByComparingTo("2.50");
                        verify(billingRepository).save(existingEc2);
                        verify(billingRepository).save(existingS3);
                        verify(billingRepository, times(2)).save(any(AwsBilling.class));
                    }

    private GetCostAndUsageResponse response() {
        return GetCostAndUsageResponse.builder()
                .resultsByTime(ResultByTime.builder()
                        .timePeriod(DateInterval.builder().start("2026-08-01").end("2026-08-02").build())
                        .total(Map.of("UnblendedCost", MetricValue.builder().amount("15.00").unit("USD").build()))
                        .groups(List.of(
                                group("us-east-1", "Amazon EC2", "12.50"),
                                group("us-east-1", "Amazon S3", "2.50")))
                        .build())
                .build();
    }

    private GetCostAndUsageResponse responseWithoutTotal() {
        return GetCostAndUsageResponse.builder()
                .resultsByTime(ResultByTime.builder()
                        .timePeriod(DateInterval.builder().start("2026-08-01").end("2026-08-02").build())
                        .groups(List.of(
                                group("us-east-1", "Amazon EC2", "12.50"),
                                group("us-east-1", "Amazon S3", "2.50")))
                        .build())
                .build();
    }

    private GetCostAndUsageResponse responseForService(String service) {
        return GetCostAndUsageResponse.builder()
                .resultsByTime(ResultByTime.builder()
                        .timePeriod(DateInterval.builder().start("2026-08-01").end("2026-08-02").build())
                        .groups(List.of(group("us-east-1", service, "4.25")))
                        .build())
                .build();
    }

    private Group group(String region, String service, String amount) {
        return Group.builder()
                .keys(region, service)
                .metrics(Map.of("UnblendedCost", MetricValue.builder().amount(amount).unit("USD").build()))
                .build();
    }

        private AwsBilling billingRow(String accountId, java.time.LocalDate date, String service, String region, String amount) {
                return new AwsBilling(accountId, date, date, service, region, java.math.BigDecimal.ONE, new java.math.BigDecimal(amount),
                                "USD", false, service, "UnblendedCost", null, "COST_EXPLORER", date, date,
                                "COST_EXPLORER|" + accountId + "|" + date + "|" + region + "|" + service, "COST_EXPLORER");
        }
}