package com.cloudcostai.aws.service.impl;

import com.cloudcostai.aws.dto.response.AwsRecommendationResponse;
import com.cloudcostai.aws.dto.response.AwsResourceResponse;
import com.cloudcostai.aws.exception.AwsConnectionException;
import com.cloudcostai.aws.model.AwsBilling;
import com.cloudcostai.aws.model.AwsRecommendation;
import com.cloudcostai.aws.repository.AwsBillingRepository;
import com.cloudcostai.aws.repository.AwsRecommendationRepository;
import com.cloudcostai.aws.service.AwsConnectionSessionService;
import com.cloudcostai.aws.service.AwsBillingSourceResolver;
import com.cloudcostai.aws.service.AwsResourceService;
import com.cloudcostai.billing.repository.BudgetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AwsRecommendationServiceImplTest {
    @Mock private AwsConnectionSessionService sessionService;
    @Mock private AwsBillingRepository billingRepository;
    @Mock private AwsRecommendationRepository recommendationRepository;
    @Mock private AwsResourceService resourceService;
    @Mock private BudgetRepository budgetRepository;

    @Test
    void noCurDataReturnsNoDataWithoutRecommendations() {
        AwsRecommendationServiceImpl service = service(List.of());

        AwsRecommendationResponse response = service.getRecommendations("token", "2026-08");

        assertThat(response.status()).isEqualTo("NO_DATA");
        assertThat(response.message()).isEqualTo("No stored Cost Explorer or CUR 2.0 billing data is available for this period.");
        assertThat(response.resourceDataStatus()).isEqualTo("NOT_CHECKED");
        assertThat(response.recommendations()).isEmpty();
    }

    @Test
    void missingAwsConnectionIsReportedInsteadOfReturningAnEmptyResult() {
        when(sessionService.getSession("")).thenThrow(
            new AwsConnectionException("AWS connection is not available. Please connect an AWS account first."));
        AwsRecommendationServiceImpl service = new AwsRecommendationServiceImpl(sessionService, billingRepository,
            recommendationRepository, new AwsBillingSourceResolver(billingRepository));

        assertThatThrownBy(() -> service.generateRecommendations("", "2026-09"))
            .isInstanceOf(AwsConnectionException.class)
            .hasMessageContaining("connect an AWS account");
    }

    @Test
    void expiredAwsConnectionIsReportedInsteadOfReturningAnEmptyResult() {
        when(sessionService.getSession("expired-token"))
            .thenThrow(new AwsConnectionException("AWS connection has expired. Please connect the account again."));
        AwsRecommendationServiceImpl service = new AwsRecommendationServiceImpl(sessionService, billingRepository,
            recommendationRepository, new AwsBillingSourceResolver(billingRepository));

        assertThatThrownBy(() -> service.generateRecommendations("expired-token", "2026-09"))
            .isInstanceOf(AwsConnectionException.class)
            .hasMessageContaining("expired");
    }

    @Test
    void getWithoutSavedFindingsReportsThatAnalysisHasNotRun() {
        AwsRecommendationServiceImpl service = service(List.of(
                row("AWS Lambda", "2026-09-10", "0.00", "lambda-zero")));

        AwsRecommendationResponse response = service.getRecommendations("token", "2026-09");

        assertThat(response.status()).isEqualTo("NO_RECOMMENDATIONS");
        assertThat(response.analysisComplete()).isFalse();
        assertThat(response.resourceDataStatus()).isEqualTo("NOT_CHECKED");
        assertThat(response.message()).isEqualTo("Recommendations have not been generated for this period yet.");
    }

    @Test
    void selectedAugustUsesAugustRowsOnlyAndDoesNotInventSaving() {
        AwsRecommendationServiceImpl service = service(List.of(
                row("Amazon EC2", "2026-07-01", "100.00", "july"),
                row("Amazon EC2", "2026-08-01", "120.00", "august"),
                row("Amazon RDS", "2026-08-31", "80.00", "august-rds"),
                row("Amazon EC2", "2026-09-01", "1000.00", "september")));
        AwsRecommendation recommendation = new AwsRecommendation("123456789012", "cost-service-Amazon EC2", "Review high-cost service", "Cost", "High", "EC2", null, "ap-south-1", new BigDecimal("120.00"), "reason", "recommendation", "action", null, "New", "2026-08");
        when(recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc("123456789012", "2026-08"))
            .thenReturn(List.of(recommendation));

        AwsRecommendationResponse response = service.getRecommendations("token", "2026-08");

        assertThat(response.accountId()).isEqualTo("123456789012");
        assertThat(response.billingMonth()).isEqualTo("2026-08");
        assertThat(response.totalSpend()).isEqualByComparingTo("200.00");
        assertThat(response.recommendations()).isEmpty();
    }

    @Test
    void unattachedEbsUsesPositiveMatchingBillingEvidence() {
        when(resourceService.discoverResources("token")).thenReturn(resources());
        AwsRecommendationServiceImpl service = serviceWithResources(List.of(
            row("Amazon EBS", "2026-08-15", "16.00", "ebs", "CUR_2_0", BigDecimal.ZERO)));
        AwsRecommendation recommendation = new AwsRecommendation("123456789012", "unused-ebs-vol-123", "Review unused EBS volume", "Unused Resource Review", "Medium", "Amazon EBS", "vol-123", "ap-south-1", new BigDecimal("16.00"), "reason", "recommendation", "action", new BigDecimal("16.00"), "New", "2026-08");
        when(recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc("123456789012", "2026-08"))
            .thenReturn(List.of(recommendation));

        AwsRecommendationResponse response = service.getRecommendations("token", "2026-08");

        assertThat(response.recommendations()).singleElement().satisfies(item -> assertThat(item.estimatedMonthlySaving()).isEqualByComparingTo("16.00"));
        assertThat(response.recommendations()).singleElement().extracting(item -> item.service()).isEqualTo("Amazon EBS");
        assertThat(response.recommendations()).singleElement().satisfies(item ->
            assertThat(item.savingsBasis()).isEqualTo("HEURISTIC_ESTIMATE"));
    }

    @Test
    void generationPreservesAnExistingStoredSavingsEstimateWhenNoNewEstimateCanBeCalculated() {
        when(resourceService.discoverResources("token")).thenReturn(resources());
        AwsRecommendationServiceImpl service = serviceWithResources(List.of(
            rowWithRegion("Amazon EBS", "2026-08-15", "16.00", null, "ap-south-1", BigDecimal.ZERO)));
        AwsRecommendation stored = new AwsRecommendation("123456789012", "unused-ebs-vol-123",
            "Previously reviewed EBS finding", "Unused Resource Review", "Medium", "Amazon EBS", "vol-123",
            "ap-south-1", new BigDecimal("16.00"), "Stored CUR evidence", "Recommendation",
            "Action", new BigDecimal("4.25"), "New", "2026-08");
        when(recommendationRepository.findByAwsAccountIdAndRecommendationKey("123456789012", "unused-ebs-vol-123"))
            .thenReturn(Optional.of(stored));
        when(recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc("123456789012", "2026-08"))
            .thenReturn(List.of(stored));

        AwsRecommendationResponse response = service.generateRecommendations("token", "2026-08");

        assertThat(response.recommendations()).singleElement()
            .satisfies(item -> assertThat(item.estimatedMonthlySaving()).isEqualByComparingTo("4.25"));
    }

    @Test
    void generationUsesPersistedCurSpendForTheSelectedMonth() {
        AwsBilling curRow = row("Amazon EC2", "2026-09-10", "150.00", "cur-ec2", "CUR_2_0");
        AwsRecommendationServiceImpl service = service(List.of(curRow));

        AwsRecommendationResponse response = service.generateRecommendations("token", "2026-09");

        assertThat(response.status()).isEqualTo("NO_RECOMMENDATIONS");
        assertThat(response.totalSpend()).isEqualByComparingTo("150.00");
        assertThat(response.recommendations()).isEmpty();
        verify(recommendationRepository, never()).save(any(AwsRecommendation.class));
    }

    @Test
    void costExplorerBillingGeneratesServiceTrendWithoutEc2ResourceDiscovery() {
        String accountId = "123456789012";
        AwsBilling previous = rowWithRegion(accountId, "Amazon EC2", "2026-08-10", "10.00", null,
            "ap-east-2", "COST_EXPLORER", BigDecimal.ONE, false);
        AwsBilling current = rowWithRegion(accountId, "Amazon EC2", "2026-09-10", "20.00", null,
            "ap-east-2", "COST_EXPLORER", BigDecimal.ONE, false);
        List<Integer> discoveryCalls = new ArrayList<>();
        com.cloudcostai.aws.service.AwsResourceService failingResourceService = new com.cloudcostai.aws.service.AwsResourceService() {
            @Override
            public AwsResourceResponse discoverResources(String token) {
                discoveryCalls.add(1);
                throw new IllegalStateException("Unable to create an EC2 client for resource discovery in ap-east-2.");
            }

            @Override
            public AwsResourceResponse discoverResourcesForRecommendations(String token, java.util.Set<String> regions) {
                discoveryCalls.add(1);
                throw new IllegalStateException("Unable to create an EC2 client for resource discovery in ap-east-2.");
            }
        };
        AwsRecommendationServiceImpl service = serviceWithBillingSources(List.of(previous, current), failingResourceService);

        AwsRecommendationResponse response = service.generateRecommendations("token", "2026-09");

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.totalSpend()).isEqualByComparingTo("20.00");
        assertThat(response.analysisComplete()).isTrue();
        assertThat(response.recommendations()).filteredOn(recommendation ->
            recommendation.problem().contains("Cost Increase Opportunity")).singleElement().satisfies(recommendation -> {
            assertThat(recommendation.dataSource()).isEqualTo("COST_EXPLORER");
            assertThat(recommendation.service()).isEqualTo("EC2");
            assertThat(recommendation.region()).isEqualTo("All regions");
            assertThat(recommendation.resourceId()).isEqualTo("N/A");
            assertThat(recommendation.currentCost()).isEqualByComparingTo("20.00");
            assertThat(recommendation.estimatedMonthlySaving()).isNull();
            assertThat(recommendation.reason()).contains("Cost Explorer", "ap-east-2", "Usage", "Amazon EC2");
        });
        assertThat(discoveryCalls).isEmpty();
    }

    @Test
    void costExplorerBillingWithoutPriorMonthStillGeneratesSupportedConcentrationFinding() {
        AwsBilling current = costExplorerRow("Amazon RDS", "2026-09-10", "20.00", "ap-east-2",
            "USE2-Storage", "Amazon RDS", "cost-concentration");
        AwsRecommendationServiceImpl service = serviceWithBillingSources(List.of(current));

        AwsRecommendationResponse response = service.generateRecommendations("token", "2026-09");

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.totalSpend()).isEqualByComparingTo("20.00");
        assertThat(response.recommendations()).anySatisfy(item -> {
            assertThat(item.problem()).contains("High service cost concentration");
            assertThat(item.reason()).contains("actual cost=20.00 USD", "total cost=20.00 USD",
                "cost share=100.00%", "billing period=2026-09",
                "stored Cost Explorer billing data in PostgreSQL aws_billing");
            assertThat(item.dataSource()).isEqualTo("COST_EXPLORER");
            assertThat(item.resourceId()).isEqualTo("N/A");
            assertThat(item.estimatedMonthlySaving()).isNull();
            assertThat(item.costSharePercentage()).isEqualByComparingTo("100.00");
            assertThat(item.periodTotalCost()).isEqualByComparingTo("20.00");
            assertThat(item.concentrationThreshold()).isEqualByComparingTo("25.00");
            assertThat(item.resourceState()).isNull();
            assertThat(item.savingsBasis()).isEqualTo("INSUFFICIENT_DATA");
        });
        verify(resourceService, never()).discoverResourcesForRecommendations(any(), any());
        verify(resourceService, never()).discoverResources(any());
    }

    @Test
    void storedCostExplorerConcentrationRulesEvaluateServiceRegionUsageAndProductWithoutDiscovery() {
        List<AwsBilling> rows = List.of(
            costExplorerRow("Amazon EC2", "2026-09-01", "75.00", "us-east-1",
                "USE1-BoxUsage", "Amazon EC2", "major"),
            costExplorerRow("Amazon RDS", "2026-09-02", "15.00", "us-west-2",
                "USW2-DBInstance", "Amazon RDS", "minor"),
            costExplorerRow("AWS Lambda", "2026-09-03", "10.00", "eu-west-1",
                "EU-Requests", "AWS Lambda", "small"));
        AwsRecommendationServiceImpl service = serviceWithBillingSources(rows);

        AwsRecommendationResponse response = service.generateRecommendations("token", "2026-09");

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.recommendations()).hasSize(4);
        assertThat(response.recommendations()).allSatisfy(item -> {
            assertThat(item.category()).isEqualTo("High Cost Service Detection");
            assertThat(item.priority()).isEqualTo("High");
            assertThat(item.currentCost()).isEqualByComparingTo("75.00");
            assertThat(item.estimatedMonthlySaving()).isNull();
            assertThat(item.reason()).contains("actual cost=75.00 USD", "total cost=100.00 USD",
                "cost share=75.00%", "billing period=2026-09", "stored Cost Explorer billing data");
            assertThat(item.dataSource()).isEqualTo("COST_EXPLORER");
            assertThat(item.resourceId()).isEqualTo("N/A");
        });
        // High Cost Service Detection recommendations should not have heuristic savings
        assertThat(response.recommendations()).allSatisfy(item ->
            assertThat(item.reason()).doesNotContain("Estimated Savings (Heuristic)"));
        assertThat(response.recommendations()).extracting(AwsRecommendationResponse.Recommendation::problem)
            .anyMatch(title -> title.contains("service cost concentration"))
            .anyMatch(title -> title.contains("region cost concentration"))
            .anyMatch(title -> title.contains("usage-type cost concentration"))
            .anyMatch(title -> title.contains("product cost concentration"));
        assertThat(response.recommendations()).anySatisfy(item ->
            assertThat(item.reason()).contains("service=Amazon EC2", "region=us-east-1",
                "usage type=USE1-BoxUsage", "product=Amazon EC2"));
        verify(resourceService, never()).discoverResourcesForRecommendations(any(), any());
        verify(resourceService, never()).discoverResources(any());
    }

    @Test
    void storedCostExplorerConcentrationFindingRemainsAvailableOnRecommendationRead() {
        String accountId = "123456789012";
        List<AwsBilling> rows = List.of(
            costExplorerRow("Amazon EC2", "2026-09-01", "75.00", "us-east-1",
                "USE1-BoxUsage", "Amazon EC2", "major"),
            costExplorerRow("Amazon RDS", "2026-09-02", "15.00", "us-west-2",
                "USW2-DBInstance", "Amazon RDS", "minor"),
            costExplorerRow("AWS Lambda", "2026-09-03", "10.00", "eu-west-1",
                "EU-Requests", "AWS Lambda", "small"));
        String key = "cost-concentration-service-" + java.util.UUID.nameUUIDFromBytes(
            (accountId + ":service:Amazon EC2").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        AwsRecommendation stored = new AwsRecommendation(accountId, key,
            "High service cost concentration: Amazon EC2", "High Cost Service Detection", "High", "EC2", null,
            "Multiple regions", new BigDecimal("75.00"), "Stored actual Cost Explorer evidence",
            "Review this service's share of stored spend.", "Review the service dimensions.", null,
            "New", "2026-09");
        when(recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(accountId, "2026-09"))
            .thenReturn(List.of(stored));
        AwsRecommendationServiceImpl service = serviceWithBillingSources(rows);

        AwsRecommendationResponse response = service.getRecommendations("token", "2026-09");

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.recommendations()).singleElement()
            .satisfies(item -> assertThat(item.problem()).contains("High service cost concentration"));
        verify(resourceService, never()).discoverResourcesForRecommendations(any(), any());
        verify(resourceService, never()).discoverResources(any());
    }

    @Test
    void costExplorerConcentrationRulesDoNotRecommendWhenNoDimensionMeetsThreshold() {
        List<AwsBilling> rows = List.of(
            costExplorerRow("Service A", "2026-09-01", "20.00", "region-a", "usage-a", "product-a", "a"),
            costExplorerRow("Service B", "2026-09-02", "20.00", "region-b", "usage-b", "product-b", "b"),
            costExplorerRow("Service C", "2026-09-03", "20.00", "region-c", "usage-c", "product-c", "c"),
            costExplorerRow("Service D", "2026-09-04", "20.00", "region-d", "usage-d", "product-d", "d"),
            costExplorerRow("Service E", "2026-09-05", "20.00", "region-e", "usage-e", "product-e", "e"));
        AwsRecommendationServiceImpl service = serviceWithBillingSources(rows);

        AwsRecommendationResponse response = service.generateRecommendations("token", "2026-09");

        assertThat(response.status()).isEqualTo("INSUFFICIENT_BILLING_EVIDENCE");
        assertThat(response.recommendations()).isEmpty();
        verify(recommendationRepository, never()).save(any(AwsRecommendation.class));
        verify(resourceService, never()).discoverResourcesForRecommendations(any(), any());
        verify(resourceService, never()).discoverResources(any());
    }

    @Test
    void septemberCostExplorerAnalysisUsesAugustBillingPeriodsAsThePreviousMonth() {
        String accountId = "123456789012";
        List<AwsBilling> rows = new ArrayList<>();
        rows.addAll(costExplorerRows(accountId, YearMonth.of(2026, 8), 185,
            new BigDecimal("7.241019"), "august"));
        rows.addAll(costExplorerRows(accountId, YearMonth.of(2026, 9), 118,
            new BigDecimal("0.003160"), "september"));
        AwsRecommendationServiceImpl service = serviceWithBillingSources(rows);

        AwsRecommendationResponse response = service.generateRecommendations("token", "2026-09");

        assertThat(response.billingMonth()).isEqualTo("2026-09");
        assertThat(response.totalSpend()).isEqualByComparingTo("0.00");
        assertThat(response.status()).isEqualTo("NO_RECOMMENDATIONS");
        assertThat(response.message()).contains("Stored Cost Explorer periods detected");
        assertThat(response.evaluationReasons()).singleElement()
            .asString()
            .contains("previous month 2026-08 (185 rows, 7.241019 USD)")
            .contains("selected month 2026-09 (118 rows, 0.003160 USD)")
            .contains("25% concentration")
            .contains("no rule qualified");
        assertThat(response.recommendations()).isEmpty();
        verify(resourceService, never()).discoverResourcesForRecommendations(any(), any());
        verify(resourceService, never()).discoverResources(any());
        verify(recommendationRepository, never()).save(any(AwsRecommendation.class));
    }

    @Test
    void generationUsesEstimatedMarkedCurRowsAsBillingEvidence() {
        AwsBilling curRow = row("AWS Lambda", "2026-08-01", "150.00", "cur-lambda", "CUR_2_0", BigDecimal.ONE, true);
        AwsRecommendationServiceImpl service = service(List.of(curRow));

        AwsRecommendationResponse response = service.generateRecommendations("token", "2026-08");

        assertThat(response.status()).isEqualTo("NO_RECOMMENDATIONS");
        assertThat(response.totalSpend()).isEqualByComparingTo("150.00");
        assertThat(response.recommendations()).isEmpty();
        verify(recommendationRepository, never()).save(any(AwsRecommendation.class));
    }

    @Test
    void stoppedEc2WithMatchingPositiveBillingGeneratesReview() {
        AwsResourceResponse stoppedInstance = new AwsResourceResponse("123456789012", "ap-south-1",
                new AwsResourceResponse.AwsResourceSummary(1, 0, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                List.of(new AwsResourceResponse.Ec2InstanceDto("i-123", "worker", "t3.micro", "stopped",
                        "ap-south-1a", "ap-south-1", null, null, null, List.of(), null)),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        when(resourceService.discoverResources("token")).thenReturn(stoppedInstance);
        AwsRecommendationServiceImpl service = serviceWithResources(List.of(
                row("Amazon EC2", "2026-09-10", "0.03", "i-123", "CUR_2_0", BigDecimal.ONE)));

        AwsRecommendationResponse response = service.generateRecommendations("token", "2026-09");

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.totalSpend()).isEqualByComparingTo("0.03");
        assertThat(response.recommendations()).singleElement().satisfies(item -> {
            assertThat(item.problem()).isEqualTo("Stopped instance still incurred cost");
            assertThat(item.priority()).isEqualTo("High");
            assertThat(item.resourceId()).isEqualTo("i-123");
            assertThat(item.currentCost()).isEqualByComparingTo("0.03");
            assertThat(item.estimatedMonthlySaving()).isEqualByComparingTo("0.03");
            assertThat(item.savingsBasis()).isEqualTo("HEURISTIC_ESTIMATE");
        });
        verify(resourceService).discoverResources("token");
    }

    @Test
    void recommendationDiscoveryUsesRegionsPresentInCurInsteadOfOnlyConnectedRegion() {
        AwsResourceResponse euWestResources = new AwsResourceResponse("123456789012", "MULTI_REGION",
            new AwsResourceResponse.AwsResourceSummary(1, 0, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            List.of(new AwsResourceResponse.Ec2InstanceDto("i-eu-west", "worker", "t3.micro", "stopped",
                "eu-west-1a", "eu-west-1", null, null, null, List.of(), null)),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        when(resourceService.discoverResourcesForRecommendations("token", java.util.Set.of("eu-west-1")))
            .thenReturn(euWestResources);
        AwsRecommendationServiceImpl service = serviceWithResources(List.of(
            rowWithRegion("Amazon EC2", "2026-09-10", "0.03", "i-eu-west", "eu-west-1", BigDecimal.ONE)));

        AwsRecommendationResponse response = service.generateRecommendations("token", "2026-09");

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.recommendations()).singleElement().satisfies(item -> {
            assertThat(item.resourceId()).isEqualTo("i-eu-west");
            assertThat(item.region()).isEqualTo("eu-west-1");
        });
        verify(resourceService).discoverResourcesForRecommendations("token", java.util.Set.of("eu-west-1"));
    }

    @Test
    void idlessCurChargesCanSupportARealResourceReviewInAnotherBilledRegion() {
        AwsResourceResponse euWestResources = new AwsResourceResponse("123456789012", "MULTI_REGION",
            new AwsResourceResponse.AwsResourceSummary(1, 0, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            List.of(new AwsResourceResponse.Ec2InstanceDto("i-eu-west", "worker", "t3.micro", "stopped",
                "eu-west-1a", "eu-west-1", null, null, null, List.of(), null)),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        when(resourceService.discoverResourcesForRecommendations("token", java.util.Set.of("eu-west-1")))
            .thenReturn(euWestResources);
        AwsRecommendationServiceImpl service = serviceWithResources(List.of(
            rowWithRegion("Amazon EC2", "2026-09-10", "0.03", null, "eu-west-1", BigDecimal.ONE)));

        AwsRecommendationResponse response = service.generateRecommendations("token", "2026-09");

        assertThat(response.recommendations()).singleElement().satisfies(item -> {
            assertThat(item.resourceId()).isEqualTo("i-eu-west");
            assertThat(item.region()).isEqualTo("eu-west-1");
            assertThat(item.currentCost()).isNull();
            assertThat(item.estimatedMonthlySaving()).isNull();
        });
    }

        @Test
        void costExplorerRowsAvoidResourceDiscoveryWhenCurRowsAreAlsoPresent() {
        String accountId = "123456789012";
        AwsBilling curDetail = rowWithRegion(accountId, "Amazon Elastic Compute Cloud - Compute", "2026-09-10",
            "0.03", "i-test123", "ap-south-1", "CUR_2_0", BigDecimal.ONE, false);
        AwsBilling aggregate = new AwsBilling(accountId, LocalDate.parse("2026-09-10"), LocalDate.parse("2026-09-11"),
            "Amazon Elastic Compute Cloud - Compute", "ap-south-1", BigDecimal.ONE, new BigDecimal("7.24"),
            "USD", false, "Amazon EC2", "UnblendedCost", null, "COST", LocalDate.parse("2026-09-10"),
            LocalDate.parse("2026-09-11"), "aggregate-ec2", "COST_EXPLORER");
        when(sessionService.getSession("token")).thenReturn(new AwsConnectionSessionService.ConnectionSession("access", "secret", "ap-south-1", accountId, Instant.now().plusSeconds(600)));
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(accountId, "CUR_2_0"))
            .thenReturn(List.of(curDetail));
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(accountId, "COST_EXPLORER"))
            .thenReturn(List.of(aggregate));
        AwsBillingSourceResolver resolver = new AwsBillingSourceResolver(billingRepository);
        AwsRecommendationServiceImpl service = new AwsRecommendationServiceImpl(sessionService, billingRepository,
            recommendationRepository, resolver, resourceService);

        AwsRecommendationResponse response = service.generateRecommendations("token", "2026-09");

        assertThat(resolver.resolve(accountId, YearMonth.parse("2026-09")).rows())
            .singleElement().extracting(AwsBilling::getResourceId).isEqualTo("N/A");
        assertThat(resolver.rows(accountId, "CUR_2_0", LocalDate.parse("2026-09-01"), LocalDate.parse("2026-10-01")))
            .singleElement().extracting(AwsBilling::getResourceId).isEqualTo("i-test123");
        assertThat(response.totalSpend()).isEqualByComparingTo("7.24");
        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.recommendations()).hasSize(4);
        assertThat(response.recommendations()).anySatisfy(item ->
            assertThat(item.problem()).contains("High service cost concentration"));
        assertThat(response.resourceDataStatus()).isEqualTo("NOT_CHECKED");
        verify(resourceService, never()).discoverResourcesForRecommendations(any(), any());
        verify(resourceService, never()).discoverResources(any());
        }

        @Test
        void stoppedEc2BillingFromAnotherRegionDoesNotGenerateFinding() {
        when(resourceService.discoverResources("token")).thenReturn(resourcesWithEc2State("stopped"));
        AwsRecommendationServiceImpl service = serviceWithResources(List.of(
            rowWithRegion("Amazon EC2", "2026-09-10", "0.03", "i-123", "us-east-1", BigDecimal.ONE)));

        AwsRecommendationResponse response = service.generateRecommendations("token", "2026-09");

        assertThat(response.status()).isEqualTo("NO_RESOURCE_BILLING_MATCH");
        assertThat(response.recommendations()).isEmpty();
        }

        @Test
        void stoppedEc2DoesNotMatchUnrelatedBillingResourceId() {
        when(resourceService.discoverResources("token")).thenReturn(resourcesWithEc2State("stopped"));
        AwsRecommendationServiceImpl service = serviceWithResources(List.of(
            row("Amazon EC2", "2026-09-10", "0.03", "i-unrelated", "CUR_2_0", BigDecimal.ONE)));

        AwsRecommendationResponse response = service.generateRecommendations("token", "2026-09");

        assertThat(response.status()).isEqualTo("NO_RESOURCE_BILLING_MATCH");
        assertThat(response.recommendations()).isEmpty();
        }

        @Test
        void stoppedEc2MatchesBillingResourceIdAfterTrimmingWhitespace() {
        when(resourceService.discoverResources("token")).thenReturn(resourcesWithEc2State("stopped"));
        AwsRecommendationServiceImpl service = serviceWithResources(List.of(
            row("Amazon EC2", "2026-09-10", "0.03", " i-123 ", "CUR_2_0", BigDecimal.ONE)));

        AwsRecommendationResponse response = service.generateRecommendations("token", "2026-09");

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.recommendations()).singleElement()
            .satisfies(item -> assertThat(item.resourceId()).isEqualTo("i-123"));
        }

        @Test
        void resourceAccountMismatchDoesNotGenerateFinding() {
        AwsResourceResponse wrongAccount = new AwsResourceResponse("000000000000", "ap-south-1",
            new AwsResourceResponse.AwsResourceSummary(1, 0, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            List.of(new AwsResourceResponse.Ec2InstanceDto("i-123", "worker", "t3.micro", "stopped",
                "ap-south-1a", "ap-south-1", null, null, null, List.of(), null)),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        when(resourceService.discoverResources("token")).thenReturn(wrongAccount);
        AwsRecommendationServiceImpl service = serviceWithResources(List.of(
            row("Amazon EC2", "2026-09-10", "0.03", "i-123", "CUR_2_0", BigDecimal.ONE)));

        AwsRecommendationResponse response = service.generateRecommendations("token", "2026-09");

        assertThat(response.status()).isEqualTo("RESOURCE_UNAVAILABLE");
        assertThat(response.resourceDataStatus()).isEqualTo("UNAVAILABLE");
        assertThat(response.recommendations()).isEmpty();
        assertThat(response.evaluationReasons()).contains("AWS resource discovery returned no data or did not match the connected account; resource matching was incomplete.");
        }

    @Test
    void stoppedEc2WithZeroBillingDoesNotGenerateFindingOrDiscoverResources() {
        when(resourceService.discoverResources("token")).thenReturn(resourcesWithEc2State("running"));
        AwsRecommendationServiceImpl service = serviceWithResources(List.of(
                row("Amazon EC2", "2026-09-10", "0.00", "i-123", "CUR_2_0", BigDecimal.ONE)));

        AwsRecommendationResponse response = service.generateRecommendations("token", "2026-09");

        assertThat(response.status()).isEqualTo("NO_RECOMMENDATIONS");
        assertThat(response.recommendations()).isEmpty();
        verify(resourceService).discoverResources("token");
        verify(recommendationRepository, never()).save(any(AwsRecommendation.class));
    }

    @Test
    void runningEc2WithPositiveBillingDoesNotGenerateStoppedInstanceFinding() {
        when(resourceService.discoverResources("token")).thenReturn(resourcesWithEc2State("running"));
        AwsRecommendationServiceImpl service = serviceWithResources(List.of(
                row("Amazon EC2", "2026-09-10", "0.03", "i-123", "CUR_2_0", BigDecimal.ONE)));

        AwsRecommendationResponse response = service.generateRecommendations("token", "2026-09");

        assertThat(response.status()).isEqualTo("NO_RECOMMENDATIONS");
        assertThat(response.recommendations()).isEmpty();
        verify(recommendationRepository, never()).save(any(AwsRecommendation.class));
    }

    @Test
    void resourceApiFailureIsReportedSeparatelyFromValidNoFindings() {
        when(resourceService.discoverResources("token")).thenThrow(new IllegalStateException("AWS permission denied"));
        AwsRecommendationServiceImpl service = serviceWithResources(List.of(
                row("Amazon EC2", "2026-09-10", "0.03", "i-123", "CUR_2_0", BigDecimal.ONE)));

        AwsRecommendationResponse response = service.generateRecommendations("token", "2026-09");

        assertThat(response.status()).isEqualTo("PERMISSION_DENIED");
        assertThat(response.resourceDataStatus()).isEqualTo("PERMISSION_DENIED");
        assertThat(response.analysisComplete()).isFalse();
        assertThat(response.message()).contains("ec2:DescribeRegions", "ec2:DescribeInstances", "ec2:DescribeVolumes");
        assertThat(response.recommendations()).isEmpty();
    }

    @Test
    void ec2PermissionFailureIsReportedSeparatelyFromOtherResourceFailures() {
        var accessDenied = software.amazon.awssdk.services.ec2.model.Ec2Exception.builder()
            .message("Access denied")
            .statusCode(403)
            .build();
        when(resourceService.discoverResources("token"))
            .thenThrow(new com.cloudcostai.aws.exception.AwsConnectionException("AWS resource read failed", accessDenied));
        AwsRecommendationServiceImpl service = serviceWithResources(List.of(
            row("Amazon EC2", "2026-09-10", "0.03", null, "CUR_2_0", BigDecimal.ONE)));

        AwsRecommendationResponse response = service.generateRecommendations("token", "2026-09");

        assertThat(response.status()).isEqualTo("PERMISSION_DENIED");
        assertThat(response.resourceDataStatus()).isEqualTo("PERMISSION_DENIED");
        assertThat(response.message()).contains("ec2:DescribeInstances", "ec2:DescribeVolumes");
        assertThat(response.recommendations()).isEmpty();
    }

    @Test
    void missingResourceIdsDoNotAllowMatchingAcrossRegions() {
        when(resourceService.discoverResources("token")).thenReturn(resourcesWithEc2State("stopped"));
        AwsRecommendationServiceImpl service = serviceWithResources(List.of(
            rowWithRegion("Amazon EC2", "2026-09-10", "0.03", null, "us-east-1", BigDecimal.ONE)));

        AwsRecommendationResponse response = service.generateRecommendations("token", "2026-09");

        assertThat(response.status()).isEqualTo("INSUFFICIENT_BILLING_EVIDENCE");
        assertThat(response.recommendations()).isEmpty();
    }

    @Test
    void stoppedEc2WithMissingBillingResourceIdReportsMissingEvidence() {
        when(resourceService.discoverResources("token")).thenReturn(resourcesWithEc2State("stopped"));
        AwsRecommendationServiceImpl service = serviceWithResources(List.of(
                rowWithRegion("Amazon EC2", "2026-09-10", "0.03", null, "ap-south-1", BigDecimal.ONE)));

        AwsRecommendationResponse response = service.generateRecommendations("token", "2026-09");

        assertThat(response.recommendations()).singleElement().satisfies(item -> {
            assertThat(item.resourceId()).isEqualTo("i-123");
            assertThat(item.currentCost()).isNull();
            assertThat(item.estimatedMonthlySaving()).isNull();
            assertThat(item.reason()).contains("cannot be attributed to this instance");
        });
    }

    @Test
    void persistedIdlessResourceReviewSurvivesStoredFindingRevalidation() {
        when(resourceService.discoverResources("token")).thenReturn(resourcesWithEc2State("stopped"));
        AwsRecommendationServiceImpl service = serviceWithResources(List.of(
            rowWithRegion("Amazon EC2", "2026-09-10", "0.03", null, "ap-south-1", BigDecimal.ONE)));
        List<AwsRecommendation> persisted = new ArrayList<>();
        when(recommendationRepository.findByAwsAccountIdAndRecommendationKey(eq("123456789012"), any()))
            .thenAnswer(invocation -> persisted.stream()
                .filter(item -> item.getRecommendationKey().equals(invocation.getArgument(1, String.class)))
                .findFirst());
        when(recommendationRepository.save(any(AwsRecommendation.class))).thenAnswer(invocation -> {
            AwsRecommendation saved = invocation.getArgument(0);
            persisted.removeIf(item -> item.getRecommendationKey().equals(saved.getRecommendationKey()));
            persisted.add(saved);
            return saved;
        });
        when(recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc("123456789012", "2026-09"))
            .thenAnswer(invocation -> List.copyOf(persisted));

        AwsRecommendationResponse generated = service.generateRecommendations("token", "2026-09");
        AwsRecommendationResponse fetched = service.getRecommendations("token", "2026-09");

        assertThat(generated.recommendations()).singleElement().satisfies(item -> {
            assertThat(item.resourceId()).isEqualTo("i-123");
            assertThat(item.currentCost()).isNull();
        });
        assertThat(fetched.recommendations()).singleElement().satisfies(item -> {
            assertThat(item.resourceId()).isEqualTo("i-123");
            assertThat(item.currentCost()).isNull();
        });
    }

        @Test
        void getRecommendationsRejectsGenericSpendFindingEvenWhenCurRowsAreEstimated() {
        AwsBilling curRow = row("AWS Lambda", "2026-08-01", "150.00", "cur-lambda", "CUR_2_0", BigDecimal.ONE, true);
        AwsRecommendation recommendation = new AwsRecommendation("123456789012", "cost-service-AWS Lambda",
            "Lambda Cost Optimization Opportunity", "Cost", "High", "Lambda", null, "All regions",
            new BigDecimal("150.00"), "reason", "recommendation", "action", null, "New", "2026-08");
        when(recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc("123456789012", "2026-08"))
            .thenReturn(List.of(recommendation));
        AwsRecommendationServiceImpl service = service(List.of(curRow));

        AwsRecommendationResponse response = service.getRecommendations("token", "2026-08");

        assertThat(response.status()).isEqualTo("NO_RECOMMENDATIONS");
        assertThat(response.billingMonth()).isEqualTo("2026-08");
        assertThat(response.totalSpend()).isEqualByComparingTo("150.00");
        assertThat(response.recommendations()).isEmpty();
        }

        @Test
        void apiRejectsLegacyGenericSpendFindingsForSelectedAccountAndMonth() {
        AwsRecommendation ec2 = new AwsRecommendation("123456789012", "cost-service-Amazon EC2",
            "EC2 Cost Optimization Opportunity", "Cost", "High", "EC2", null, "All regions",
            new BigDecimal("150.00"), "reason", "recommendation", "action", null, "New", "2026-08");
        AwsRecommendation rds = new AwsRecommendation("123456789012", "cost-service-Amazon RDS",
            "RDS Cost Optimization Opportunity", "Cost", "High", "RDS", null, "All regions",
            new BigDecimal("175.00"), "reason", "recommendation", "action", null, "New", "2026-08");
        AwsRecommendationServiceImpl service = service(List.of(
            row("Amazon EC2", "2026-08-05", "150.00", "ec2"),
            row("Amazon RDS", "2026-08-06", "175.00", "rds")));
        when(recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc("123456789012", "2026-08"))
            .thenReturn(List.of(ec2, rds));

        AwsRecommendationResponse response = service.getRecommendations("token", "2026-08");

        assertThat(response.accountId()).isEqualTo("123456789012");
        assertThat(response.billingMonth()).isEqualTo("2026-08");
        assertThat(response.recommendations()).isEmpty();
        verify(billingRepository)
            .findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc("123456789012", "CUR_2_0");
        }

        @Test
    void zeroCostLambdaDoesNotCreateAnOptimizationFinding() {
        AwsRecommendationServiceImpl service = service(List.of(row("AWS Lambda", "2026-09-10", "0.00", "lambda-zero")));

        AwsRecommendationResponse response = service.generateRecommendations("token", "2026-09");

        assertThat(response.recommendations()).isEmpty();
        verify(recommendationRepository, never()).save(any(AwsRecommendation.class));
    }

    @Test
    void highServiceSpendWithoutQualifyingResourceEvidenceDoesNotGenerateFinding() {
        AwsRecommendationServiceImpl service = service(List.of(row("AWS Lambda", "2026-09-10", "150.00", "lambda-paid")));

        AwsRecommendationResponse response = service.generateRecommendations("token", "2026-09");

        assertThat(response.status()).isEqualTo("NO_RECOMMENDATIONS");
        assertThat(response.totalSpend()).isEqualByComparingTo("150.00");
        assertThat(response.recommendations()).isEmpty();
        verify(recommendationRepository, never()).save(any(AwsRecommendation.class));
    }

        @Test
        void storedZeroCostServiceFindingIsHiddenWhenCurrentCurHasNoSpend() {
        AwsRecommendation stale = new AwsRecommendation("123456789012", "cost-service-AWS Lambda",
            "High service cost", "Cost", "High", "Lambda", null, "All regions", BigDecimal.ZERO,
            "Old evidence", "Old recommendation", "Old action", null, "NEW", "2026-09");
        when(recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc("123456789012", "2026-09"))
            .thenReturn(List.of(stale));
        AwsRecommendationServiceImpl service = service(List.of(row("AWS Lambda", "2026-09-10", "0.00", "lambda-zero")));

        AwsRecommendationResponse response = service.getRecommendations("token", "2026-09");

        assertThat(response.status()).isEqualTo("NO_RECOMMENDATIONS");
        assertThat(response.recommendations()).isEmpty();
        }

    @Test
    void ebsCleanupRequiresUnattachedResourceAndPositiveBillingEvidence() {
        when(resourceService.discoverResources("token")).thenReturn(resources());
        AwsRecommendationServiceImpl service = serviceWithResources(List.of(
                row("Amazon EBS", "2026-09-10", "16.00", "ebs", "CUR_2_0", BigDecimal.ZERO)));

        AwsRecommendationResponse response = service.generateRecommendations("token", "2026-09");

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.recommendations()).singleElement()
            .extracting(item -> item.problem()).isEqualTo("Unattached volume is incurring cost");
        assertThat(response.recommendations()).singleElement().satisfies(item -> {
            assertThat(item.resourceState()).isEqualTo("UNATTACHED");
            assertThat(item.estimatedMonthlySaving()).isEqualByComparingTo("16.00");
            assertThat(item.savingsBasis()).isEqualTo("HEURISTIC_ESTIMATE");
        });
        ArgumentCaptor<AwsRecommendation> saved = ArgumentCaptor.forClass(AwsRecommendation.class);
        verify(recommendationRepository, org.mockito.Mockito.times(1)).save(saved.capture());
        assertThat(saved.getValue().getTitle()).isEqualTo("Unattached volume is incurring cost");
        assertThat(saved.getValue().getReason()).contains("actual charges", "unattached");
        assertThat(saved.getValue().getPotentialSavings()).isEqualByComparingTo("16.00");
    }

    @Test
    void unattachedEbsMatchesBillingResourceIdAfterTrimmingWhitespace() {
        when(resourceService.discoverResources("token")).thenReturn(resources());
        AwsRecommendationServiceImpl service = serviceWithResources(List.of(
                rowWithRegion("Amazon EBS", "2026-09-10", "16.00", " vol-123 ", "ap-south-1", BigDecimal.ZERO)));

        AwsRecommendationResponse response = service.generateRecommendations("token", "2026-09");

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.recommendations()).singleElement().satisfies(item -> {
            assertThat(item.problem()).isEqualTo("Unattached volume is incurring cost");
            assertThat(item.resourceId()).isEqualTo("vol-123");
        });
    }

    @Test
    void unattachedEbsBillingFromAnotherRegionDoesNotGenerateFinding() {
        when(resourceService.discoverResources("token")).thenReturn(resources());
        AwsRecommendationServiceImpl service = serviceWithResources(List.of(
                rowWithRegion("Amazon EBS", "2026-09-10", "16.00", "vol-123", "us-east-1", BigDecimal.ZERO)));

        AwsRecommendationResponse response = service.generateRecommendations("token", "2026-09");

        assertThat(response.status()).isEqualTo("NO_RESOURCE_BILLING_MATCH");
        assertThat(response.recommendations()).isEmpty();
        verify(recommendationRepository, never()).save(any(AwsRecommendation.class));
    }

    @Test
    void unattachedEbsWithMissingBillingResourceIdReportsMissingEvidence() {
        when(resourceService.discoverResources("token")).thenReturn(resources());
        AwsRecommendationServiceImpl service = serviceWithResources(List.of(
                rowWithRegion("Amazon EBS", "2026-09-10", "16.00", null, "ap-south-1", BigDecimal.ZERO)));

        AwsRecommendationResponse response = service.generateRecommendations("token", "2026-09");

        assertThat(response.recommendations()).singleElement().satisfies(item -> {
            assertThat(item.resourceId()).isEqualTo("vol-123");
            assertThat(item.currentCost()).isNull();
            assertThat(item.estimatedMonthlySaving()).isNull();
            assertThat(item.reason()).contains("cannot be attributed to this volume");
        });
    }

    @Test
    void zeroUsageEbsIsNotRecommendedWhenLiveResourceIsAttached() {
        AwsResourceResponse attached = new AwsResourceResponse("123456789012", "ap-south-1",
                new AwsResourceResponse.AwsResourceSummary(1, 1, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0),
                List.of(), List.of(new AwsResourceResponse.EbsVolumeDto("vol-123", "gp3", 100, "in-use",
                        "ap-south-1a", "i-123")), List.of(), List.of(), List.of(), List.of(), List.of());
        when(resourceService.discoverResources("token")).thenReturn(attached);
        AwsRecommendationServiceImpl service = serviceWithResources(List.of(
                row("Amazon EBS", "2026-09-10", "16.00", "ebs", "CUR_2_0", BigDecimal.ZERO)));

        AwsRecommendationResponse response = service.generateRecommendations("token", "2026-09");

        assertThat(response.status()).isEqualTo("NO_RECOMMENDATIONS");
        verify(recommendationRepository, never()).save(any(AwsRecommendation.class));
    }

    @Test
        void recommendationsUseStoredCostExplorerRowsWhenAvailable() {
            String accountId = "399779591966";
            AwsBilling curRow = row(accountId, "Amazon EC2", "2026-08-01", "5.38", "august-cur", "CUR_2_0", BigDecimal.ONE, true);
            AwsBilling resolvedRow = row(accountId, "Amazon RDS", "2026-08-06", "7.241019", "august-resolved", "COST_EXPLORER", BigDecimal.ONE, true);
            AwsRecommendation storedRecommendation = new AwsRecommendation(accountId, "cost-service-Amazon RDS",
                "Old RDS finding", "Cost", "High", "RDS", null, "All regions", new BigDecimal("5.38"),
                "Old evidence", "Old recommendation", "Old action", null, "New", "2026-08");
            when(sessionService.getSession("token")).thenReturn(new AwsConnectionSessionService.ConnectionSession("access", "secret", "ap-south-1", accountId, Instant.now().plusSeconds(600)));
            when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(accountId, "CUR_2_0"))
            .thenReturn(List.of(curRow));
            when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(accountId, "COST_EXPLORER"))
            .thenReturn(List.of(resolvedRow));
            when(recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(accountId, "2026-08"))
                .thenReturn(List.of(storedRecommendation));
        AwsBillingSourceResolver resolver = new AwsBillingSourceResolver(billingRepository);
        AwsRecommendationServiceImpl recommendations = new AwsRecommendationServiceImpl(sessionService, billingRepository,
            recommendationRepository, resolver);
        AwsCostAnalyticsServiceImpl analytics = new AwsCostAnalyticsServiceImpl(sessionService, billingRepository,
            recommendationRepository, budgetRepository, resolver);
        AwsInsightServiceImpl insights = new AwsInsightServiceImpl(sessionService, billingRepository, null, null, resolver);

        BigDecimal analyticsCost = analytics.getAnalytics("token", "CURRENT_MONTH", "2026-08").summary().totalCost();
        BigDecimal insightsCost = insights.getInsights("token", "2026-08").totalSpend();
        AwsRecommendationResponse recommendationResponse = recommendations.getRecommendations("token", "2026-08");

        assertThat(analyticsCost).isEqualByComparingTo("7.24");
        assertThat(insightsCost.setScale(2, RoundingMode.HALF_UP)).isEqualByComparingTo(analyticsCost);
        assertThat(recommendationResponse.totalSpend()).isEqualByComparingTo("7.24");
        assertThat(recommendationResponse.status()).isEqualTo("INSUFFICIENT_BILLING_EVIDENCE");
        assertThat(recommendationResponse.recommendations()).isEmpty();
    }

    @Test
    void storedResourceFindingAndTotalsUseCurDetailOnly() {
        String accountId = "399779591966";
        AwsBilling curDetail = row(accountId, "Amazon Elastic Compute Cloud - Compute", "2026-09-10",
            "0.03", "i-test123", "CUR_2_0", BigDecimal.ONE, false);
        AwsRecommendation stoppedFinding = new AwsRecommendation(accountId, "stopped-ec2-i-test123",
            "Stopped EC2 Instance Cost Review", "Unused Resource Review", "Medium", "EC2", "i-test123", "ap-south-1",
            new BigDecimal("0.03"), "Matched positive CUR charge", "Evidence", "Review the instance", null,
            "New", "2026-09");
        AwsResourceResponse stoppedResource = new AwsResourceResponse(accountId, "ap-south-1",
            new AwsResourceResponse.AwsResourceSummary(1, 0, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            List.of(new AwsResourceResponse.Ec2InstanceDto("i-test123", "worker", "t3.micro", "stopped",
                "ap-south-1a", "ap-south-1", null, null, null, List.of(), null)),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        when(sessionService.getSession("token")).thenReturn(new AwsConnectionSessionService.ConnectionSession(
            "access", "secret", "ap-south-1", accountId, Instant.now().plusSeconds(600)));
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(accountId, "CUR_2_0"))
            .thenReturn(List.of(curDetail));
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(accountId, "COST_EXPLORER"))
            .thenReturn(List.of());
        when(recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(accountId, "2026-09"))
            .thenReturn(List.of(stoppedFinding));
        when(resourceService.discoverResources("token")).thenReturn(stoppedResource);
        AwsRecommendationServiceImpl service = new AwsRecommendationServiceImpl(sessionService, billingRepository,
            recommendationRepository, new AwsBillingSourceResolver(billingRepository), resourceService);

        AwsRecommendationResponse response = service.getRecommendations("token", "2026-09");

        assertThat(response.totalSpend()).isEqualByComparingTo("0.03");
        assertThat(response.recommendations()).singleElement().satisfies(item -> {
            assertThat(item.resourceId()).isEqualTo("i-test123");
            assertThat(item.currentCost()).isEqualByComparingTo("0.03");
        });
    }

    @Test
    void storedServiceSpikeUsesCurPreviousMonthRows() {
        String accountId = "399779591966";
        AwsBilling curCurrent = row(accountId, "Amazon RDS", "2026-09-10", "5.38", "cur-current",
            "CUR_2_0", BigDecimal.ONE, false);
        AwsBilling curPrevious = row(accountId, "Amazon RDS", "2026-08-10", "2.00", "cur-previous",
            "CUR_2_0", BigDecimal.ONE, false);
        AwsRecommendation staleSpike = new AwsRecommendation(accountId, "spike-service-Amazon RDS",
            "RDS Cost Increase Opportunity", "Trend", "High", "RDS", null, "All regions",
            new BigDecimal("5.38"), "Old evidence", "Old recommendation", "Old action", null,
            "New", "2026-09");
        when(sessionService.getSession("token")).thenReturn(new AwsConnectionSessionService.ConnectionSession(
            "access", "secret", "ap-south-1", accountId, Instant.now().plusSeconds(600)));
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(accountId, "CUR_2_0"))
            .thenReturn(List.of(curPrevious, curCurrent));
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(accountId, "COST_EXPLORER"))
            .thenReturn(List.of());
        when(recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(accountId, "2026-09"))
            .thenReturn(List.of(staleSpike));
        AwsRecommendationServiceImpl service = new AwsRecommendationServiceImpl(sessionService, billingRepository,
            recommendationRepository, new AwsBillingSourceResolver(billingRepository), resourceService);

        AwsRecommendationResponse response = service.getRecommendations("token", "2026-09");

        assertThat(response.totalSpend()).isEqualByComparingTo("5.38");
        assertThat(response.recommendations()).singleElement()
            .satisfies(item -> assertThat(item.problem()).isEqualTo("RDS Cost Increase Opportunity"));
    }

        @Test
        void augustServiceSpendDoesNotCreateGenericCostFinding() {
        String accountId = "399779591966";
        List<AwsBilling> curRows = List.of(row(accountId, "Amazon RDS", "2026-08-01", "5.38", "cur-august", "CUR_2_0", BigDecimal.ONE, true));
        when(sessionService.getSession("token")).thenReturn(new AwsConnectionSessionService.ConnectionSession("access", "secret", "ap-south-1", accountId, Instant.now().plusSeconds(600)));
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(accountId, "CUR_2_0"))
            .thenReturn(curRows);
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(accountId, "COST_EXPLORER"))
            .thenReturn(List.of());
        when(resourceService.discoverResources("token")).thenReturn(emptyResources(accountId));
        AwsRecommendationServiceImpl service = new AwsRecommendationServiceImpl(sessionService, billingRepository,
            recommendationRepository, new AwsBillingSourceResolver(billingRepository), resourceService);

        AwsRecommendationResponse response = service.generateRecommendations("token", "2026-08");

        assertThat(response.status()).isEqualTo("NO_RESOURCES");
        assertThat(response.analysisComplete()).isTrue();
        assertThat(response.resourceDataStatus()).isEqualTo("NO_RESOURCES");
        assertThat(response.totalSpend()).isEqualByComparingTo("5.38");
        assertThat(response.recommendations()).isEmpty();
        assertThat(response.message()).isEqualTo("AWS resource discovery found no EC2 or EBS resources in any enabled region for this account.");
        assertThat(response.evaluationReasons()).doesNotContain("No services exceeded the high-cost threshold of 100.00.");
        verify(recommendationRepository, never()).save(any(AwsRecommendation.class));
        }

        @Test
        void unknownLegacyFindingIsNotTreatedAsSupported() {
        String accountId = "399779591966";
        AwsRecommendation legacy = new AwsRecommendation(accountId,
            "399779591966:2026-08:HIGH_COST_SERVICE:AWS Cost Explorer", "Review high-cost service",
            "OPTIMIZATION_OPPORTUNITY", "Medium", "AWS Cost Explorer", null, "All regions",
            new BigDecimal("2.43"), "Persisted legacy evidence", "Old recommendation", "Old action", null,
            "Reviewed", "2026-08");
        when(sessionService.getSession("token")).thenReturn(new AwsConnectionSessionService.ConnectionSession("access", "secret", "ap-south-1", accountId, Instant.now().plusSeconds(600)));
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(accountId, "CUR_2_0"))
            .thenReturn(List.of());
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(accountId, "COST_EXPLORER"))
            .thenReturn(List.of());
        AwsRecommendationServiceImpl service = new AwsRecommendationServiceImpl(sessionService, billingRepository,
            recommendationRepository, new AwsBillingSourceResolver(billingRepository));

        AwsRecommendationResponse response = service.getRecommendations("token", "2026-08");

        assertThat(response.status()).isEqualTo("NO_DATA");
        assertThat(response.recommendations()).isEmpty();
        }

    private AwsRecommendationServiceImpl service(List<AwsBilling> rows) {
        when(sessionService.getSession("token")).thenReturn(new AwsConnectionSessionService.ConnectionSession("access", "secret", "ap-south-1", "123456789012", Instant.now().plusSeconds(600)));
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc("123456789012", "CUR_2_0"))
            .thenReturn(rows.stream().filter(row -> "CUR_2_0".equals(row.getDataSource())).toList());
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc("123456789012", "COST_EXPLORER"))
            .thenReturn(List.of());
        return new AwsRecommendationServiceImpl(sessionService, billingRepository, recommendationRepository,
            new AwsBillingSourceResolver(billingRepository));
    }

    private AwsRecommendationServiceImpl serviceWithResources(List<AwsBilling> rows) {
        when(sessionService.getSession("token")).thenReturn(new AwsConnectionSessionService.ConnectionSession("access", "secret", "ap-south-1", "123456789012", Instant.now().plusSeconds(600)));
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc("123456789012", "CUR_2_0"))
            .thenReturn(rows.stream().filter(row -> "CUR_2_0".equals(row.getDataSource())).toList());
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc("123456789012", "COST_EXPLORER"))
            .thenReturn(List.of());
        return new AwsRecommendationServiceImpl(sessionService, billingRepository, recommendationRepository,
            new AwsBillingSourceResolver(billingRepository), resourceService);
    }

    private AwsRecommendationServiceImpl serviceWithBillingSources(List<AwsBilling> rows) {
        return serviceWithBillingSources(rows, resourceService);
    }

    private AwsRecommendationServiceImpl serviceWithBillingSources(List<AwsBilling> rows,
                                                                    com.cloudcostai.aws.service.AwsResourceService discoveryService) {
        String accountId = "123456789012";
        when(sessionService.getSession("token")).thenReturn(new AwsConnectionSessionService.ConnectionSession(
            "access", "secret", "ap-south-1", accountId, Instant.now().plusSeconds(600)));
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(accountId, "CUR_2_0"))
            .thenReturn(rows.stream().filter(row -> "CUR_2_0".equals(row.getDataSource())).toList());
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(accountId, "COST_EXPLORER"))
            .thenReturn(rows.stream().filter(row -> "COST_EXPLORER".equals(row.getDataSource())).toList());
        return new AwsRecommendationServiceImpl(sessionService, billingRepository, recommendationRepository,
            new AwsBillingSourceResolver(billingRepository), discoveryService);
    }

    private List<AwsBilling> costExplorerRows(String accountId, YearMonth month, int count,
                                              BigDecimal total, String keyPrefix) {
        BigDecimal perRow = total.divide(BigDecimal.valueOf(count), 6, RoundingMode.DOWN);
        BigDecimal lastRow = total.subtract(perRow.multiply(BigDecimal.valueOf(count - 1L)));
        List<AwsBilling> rows = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            LocalDate billingDate = month.atDay(1).plusDays(index % month.lengthOfMonth());
            BigDecimal cost = index == count - 1 ? lastRow : perRow;
            String bucket = Integer.toString(index % 5);
            rows.add(new AwsBilling(accountId, billingDate, billingDate, "Amazon EC2 " + bucket, "ap-east-2-" + bucket,
                BigDecimal.ONE, cost, "USD", false, "Amazon EC2 " + bucket, "Usage " + bucket, null, "COST_EXPLORER",
                null, null, keyPrefix + "-" + index, "COST_EXPLORER"));
        }
        return rows;
    }

    private AwsBilling costExplorerRow(String service, String date, String cost, String region,
                                       String usageType, String productName, String key) {
        LocalDate billingDate = LocalDate.parse(date);
        return new AwsBilling("123456789012", billingDate, billingDate.plusDays(1), service, region,
            BigDecimal.ONE, new BigDecimal(cost), "USD", false, productName, usageType, null,
            "Usage", billingDate, billingDate.plusDays(1), key, "COST_EXPLORER");
    }

    private AwsResourceResponse resources() {
        return new AwsResourceResponse("123456789012", "ap-south-1", new AwsResourceResponse.AwsResourceSummary(1, 0, 0, 0, 0, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0), List.of(), List.of(new AwsResourceResponse.EbsVolumeDto("vol-123", "gp3", 100, "available", "ap-south-1a", null)), List.of(), List.of(), List.of(), List.of(), List.of());
    }

        private AwsResourceResponse resourcesWithEc2State(String state) {
        return new AwsResourceResponse("123456789012", "ap-south-1",
            new AwsResourceResponse.AwsResourceSummary(1, 0, 1, 0, "stopped".equals(state) ? 1 : 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            List.of(new AwsResourceResponse.Ec2InstanceDto("i-123", "worker", "t3.micro", state,
                "ap-south-1a", "ap-south-1", null, null, null, List.of(), null)),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }

    private AwsResourceResponse emptyResources(String accountId) {
        return new AwsResourceResponse(accountId, "ap-south-1",
                new AwsResourceResponse.AwsResourceSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private AwsBilling row(String service, String date, String cost, String key) {
        return row(service, date, cost, key, "CUR_2_0");
    }

    private AwsBilling row(String service, String date, String cost, String key, String source) {
        return row(service, date, cost, key, source, BigDecimal.ONE);
    }

    private AwsBilling row(String service, String date, String cost, String key, String source, BigDecimal usage) {
        return row(service, date, cost, key, source, usage, false);
    }

    private AwsBilling row(String service, String date, String cost, String key, String source, BigDecimal usage, boolean estimated) {
        return row("123456789012", service, date, cost, key, source, usage, estimated);
    }

    private AwsBilling row(String accountId, String service, String date, String cost, String key, String source, BigDecimal usage, boolean estimated) {
        return rowWithRegion(accountId, service, date, cost, key, "ap-south-1", source, usage, estimated);
    }

    private AwsBilling rowWithRegion(String service, String date, String cost, String resourceId, String region, BigDecimal usage) {
        return rowWithRegion("123456789012", service, date, cost, resourceId, region, "CUR_2_0", usage, false);
    }

    private AwsBilling rowWithRegion(String accountId, String service, String date, String cost, String resourceId,
                                     String region, String source, BigDecimal usage, boolean estimated) {
        LocalDate usageDate = LocalDate.parse(date);
        String billingResourceId = "ebs".equals(resourceId) ? "vol-123" : resourceId;
        return new AwsBilling(accountId, usageDate, usageDate.plusDays(1), service, region, usage,
            new BigDecimal(cost), "USD", estimated, service, "Usage", billingResourceId,
            "Usage", usageDate, usageDate.plusDays(1), billingResourceId, source);
    }
}
