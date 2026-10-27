package com.cloudcostai.aws.service.impl;

import com.cloudcostai.aws.dto.request.CostSimulationRequest;
import com.cloudcostai.aws.model.AwsBilling;
import com.cloudcostai.aws.model.AwsRecommendation;
import com.cloudcostai.aws.repository.AwsBillingRepository;
import com.cloudcostai.aws.repository.AwsRecommendationRepository;
import com.cloudcostai.aws.service.AwsBillingSourceResolver;
import com.cloudcostai.aws.service.AwsConnectionSessionService;
import com.cloudcostai.aws.service.CostSimulatorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CostSimulatorServiceTest {
    private static final String ACCOUNT_ID = "123456789012";

    @Mock private AwsConnectionSessionService sessionService;
    @Mock private AwsBillingRepository billingRepository;
    @Mock private AwsRecommendationRepository recommendationRepository;

    @Test
    void simulatesUsingCurrentAccountBillingAndSavedRecommendationSavings() {
        AwsRecommendation ec2 = recommendation("Amazon EC2", "i-live-1", "72.00");
        AwsRecommendation ebs = recommendation("Amazon EBS", "vol-live-1", "62.00");
        stubBilling(List.of(billingRow(LocalDate.of(2026, 9, 1), "400.00"),
                billingRow(LocalDate.of(2026, 9, 10), "20.00")));
        when(recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(ACCOUNT_ID, "2026-09"))
                .thenReturn(List.of(ec2, ebs));

        var result = service().simulate("token", new CostSimulationRequest("2026-09", List.of(ec2.getId(), ebs.getId())));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(result.dataSource()).isEqualTo("CUR_2_0");
        assertThat(result.currentMonthlyCost()).isEqualByComparingTo("420.00");
        assertThat(result.estimatedMonthlySaving()).isEqualByComparingTo("134.00");
        assertThat(result.estimatedMonthlyCost()).isEqualByComparingTo("286.00");
        assertThat(result.estimatedAnnualSaving()).isEqualByComparingTo("1608.00");
        verify(recommendationRepository, never()).save(ec2);
        verify(recommendationRepository, never()).save(ebs);
    }

    @Test
    void doesNotReturnZeroWhenBillingDataIsUnavailable() {
        stubBilling(List.of());

        var result = service().simulate("token", new CostSimulationRequest("2026-09", List.of(UUID.randomUUID())));

        assertThat(result.status()).isEqualTo("NO_BILLING_DATA");
        assertThat(result.currentMonthlyCost()).isNull();
        assertThat(result.estimatedMonthlyCost()).isNull();
    }

    @Test
    void unavailableSavedPricingDoesNotProduceAggregateEstimate() {
        AwsRecommendation noEstimate = recommendation("Amazon RDS", "db-live-1", null);
        stubBilling(List.of(billingRow(LocalDate.of(2026, 9, 1), "420.00")));
        when(recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(ACCOUNT_ID, "2026-09"))
                .thenReturn(List.of(noEstimate));

        var result = service().simulate("token", new CostSimulationRequest("2026-09", List.of(noEstimate.getId())));

        assertThat(result.status()).isEqualTo("INSUFFICIENT_PRICING_DATA");
        assertThat(result.currentMonthlyCost()).isEqualByComparingTo("420.00");
        assertThat(result.estimatedMonthlySaving()).isNull();
        assertThat(result.scenarios().get(0).status()).isEqualTo("INSUFFICIENT_PRICING_DATA");
    }

        @Test
        void appliedRecommendationRemainsUnavailable() {
                AwsRecommendation applied = recommendation("Amazon EC2", "i-applied-1", "72.00", "Applied");
                stubBilling(List.of(billingRow(LocalDate.of(2026, 9, 1), "420.00")));
                when(recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(ACCOUNT_ID, "2026-09"))
                                .thenReturn(List.of(applied));

                var result = service().simulate("token", new CostSimulationRequest("2026-09", List.of(applied.getId())));

                assertThat(result.status()).isEqualTo("INSUFFICIENT_PRICING_DATA");
                assertThat(result.scenarios().get(0).status()).isEqualTo("ALREADY_APPLIED");
                assertThat(result.scenarios().get(0).estimatedMonthlySaving()).isNull();
                verify(recommendationRepository, never()).save(applied);
        }

        @Test
        void dismissedRecommendationIsNotActionable() {
                AwsRecommendation dismissed = recommendation("Amazon RDS", "db-dismissed-1", "72.00", "Dismissed");
                stubBilling(List.of(billingRow(LocalDate.of(2026, 9, 1), "420.00")));
                when(recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(ACCOUNT_ID, "2026-09"))
                                .thenReturn(List.of(dismissed));

                var result = service().simulate("token", new CostSimulationRequest("2026-09", List.of(dismissed.getId())));

                assertThat(result.status()).isEqualTo("INSUFFICIENT_PRICING_DATA");
                assertThat(result.scenarios().get(0).status()).isEqualTo("NOT_ACTIONABLE");
        }

        @Test
        void negativeSavedSavingsAreUnavailable() {
                AwsRecommendation negative = recommendation("Amazon RDS", "db-negative-1", "-1.00");
                stubBilling(List.of(billingRow(LocalDate.of(2026, 9, 1), "420.00")));
                when(recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(ACCOUNT_ID, "2026-09"))
                                .thenReturn(List.of(negative));

                var result = service().simulate("token", new CostSimulationRequest("2026-09", List.of(negative.getId())));

                assertThat(result.status()).isEqualTo("INSUFFICIENT_PRICING_DATA");
                assertThat(result.scenarios().get(0).status()).isEqualTo("INSUFFICIENT_PRICING_DATA");
                assertThat(result.estimatedMonthlySaving()).isNull();
        }

        @Test
        void acceptsAStoredZeroSavingsEstimate() {
                AwsRecommendation zero = recommendation("Amazon S3", "s3-zero-1", "0.00");
                stubBilling(List.of(billingRow(LocalDate.of(2026, 9, 1), "420.00")));
                when(recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(ACCOUNT_ID, "2026-09"))
                                .thenReturn(List.of(zero));

                var result = service().simulate("token", new CostSimulationRequest("2026-09", List.of(zero.getId())));

                assertThat(result.status()).isEqualTo("SUCCESS");
                assertThat(result.estimatedMonthlySaving()).isEqualByComparingTo("0.00");
                assertThat(result.estimatedMonthlyCost()).isEqualByComparingTo("420.00");
        }

        @Test
        void projectedCostIsClampedToZeroWhenSavedSavingsExceedSpend() {
                AwsRecommendation recommendation = recommendation("Amazon EC2", "i-over-spend-1", "500.00");
                stubBilling(List.of(billingRow(LocalDate.of(2026, 9, 1), "420.00")));
                when(recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(ACCOUNT_ID, "2026-09"))
                                .thenReturn(List.of(recommendation));

                var result = service().simulate("token", new CostSimulationRequest("2026-09", List.of(recommendation.getId())));

                assertThat(result.status()).isEqualTo("SUCCESS");
                assertThat(result.estimatedMonthlySaving()).isEqualByComparingTo("500.00");
                assertThat(result.estimatedMonthlyCost()).isEqualByComparingTo("0.00");
        }

    @Test
    void refusesRecommendationsFromAnotherAccountOrMonth() {
        AwsRecommendation otherAccountScenario = recommendation("Amazon EC2", "i-other-1", "72.00");
        stubBilling(List.of(billingRow(LocalDate.of(2026, 9, 1), "420.00")));
        when(recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(ACCOUNT_ID, "2026-09"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service().simulate("token",
                new CostSimulationRequest("2026-09", List.of(otherAccountScenario.getId()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("account and billing month");
        verify(recommendationRepository).findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(ACCOUNT_ID, "2026-09");
    }

    @Test
    void rejectsOverlappingScenarios() {
        AwsRecommendation first = recommendation("Amazon EC2", "i-live-1", "72.00");
        AwsRecommendation second = recommendation("Amazon EC2", "i-live-1", "20.00");
        stubBilling(List.of(billingRow(LocalDate.of(2026, 9, 1), "420.00")));
        when(recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(ACCOUNT_ID, "2026-09"))
                .thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service().simulate("token",
                new CostSimulationRequest("2026-09", List.of(first.getId(), second.getId()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("simulated more than once");
    }

    @Test
    void rejectsEmptySelectionAndInvalidMonth() {
        assertThatThrownBy(() -> service().simulate("token", new CostSimulationRequest("2026-09", List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Select at least one");
        assertThatThrownBy(() -> service().simulate("token", new CostSimulationRequest("2026-9", List.of(UUID.randomUUID()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("YYYY-MM");
    }

    @Test
    void derivesResourceSavingsFromMatchedBillingWhenNoStoredEstimate() {
        AwsRecommendation resource = recommendation("Amazon EC2", "i-derived-1", null);
        stubBilling(List.of(
                billingRowWithResource(LocalDate.of(2026, 9, 1), "420.00", "Amazon EC2", null),
                billingRowWithResource(LocalDate.of(2026, 9, 10), "30.00", "Amazon EC2", "i-derived-1")));
        when(recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(ACCOUNT_ID, "2026-09"))
                .thenReturn(List.of(resource));

        var result = service().simulate("token", new CostSimulationRequest("2026-09", List.of(resource.getId())));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.currentMonthlyCost()).isEqualByComparingTo("450.00");
        assertThat(result.estimatedMonthlySaving()).isEqualByComparingTo("30.00");
        assertThat(result.scenarios().get(0).status()).isEqualTo("AVAILABLE");
        assertThat(result.scenarios().get(0).estimateBasis()).isEqualTo("DERIVED_RESOURCE_COST");
    }

    @Test
    void derivesServiceRateSavingsWhenNoStoredEstimateAndNoResourceMatch() {
        AwsRecommendation serviceLevel = recommendation("Amazon EC2", null, null);
        stubBilling(List.of(billingRow(LocalDate.of(2026, 9, 1), "400.00"),
                billingRow(LocalDate.of(2026, 9, 10), "20.00")));
        when(recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(ACCOUNT_ID, "2026-09"))
                .thenReturn(List.of(serviceLevel));

        var result = service().simulate("token", new CostSimulationRequest("2026-09", List.of(serviceLevel.getId())));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.estimatedMonthlySaving()).isEqualByComparingTo("42.00");
        assertThat(result.scenarios().get(0).estimateBasis()).isEqualTo("DERIVED_SERVICE_RATE");
    }

    @Test
    void whatIfReductionProjectsProportionalSaving() {
        stubBilling(List.of(billingRow(LocalDate.of(2026, 9, 1), "400.00"),
                billingRow(LocalDate.of(2026, 9, 10), "20.00")));

        var result = service().simulate("token",
                new CostSimulationRequest("2026-09", null, new BigDecimal("25")));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.mode()).isEqualTo("WHAT_IF");
        assertThat(result.estimatedMonthlySaving()).isEqualByComparingTo("105.00");
        assertThat(result.estimatedMonthlyCost()).isEqualByComparingTo("315.00");
        assertThat(result.estimatedAnnualSaving()).isEqualByComparingTo("1260.00");
        assertThat(result.whatIfReductionPercent()).isEqualByComparingTo("25.00");
        assertThat(result.scenarios()).singleElement().satisfies(scenario -> {
            assertThat(scenario.estimateBasis()).isEqualTo("WHAT_IF");
            assertThat(scenario.estimatedMonthlySaving()).isEqualByComparingTo("105.00");
        });
    }

    @Test
    void rejectsMixingWhatIfReductionWithRecommendations() {
        assertThatThrownBy(() -> service().simulate("token",
                new CostSimulationRequest("2026-09", List.of(UUID.randomUUID()), new BigDecimal("10"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not both");
    }

    @Test
    void rejectsInvalidWhatIfPercentage() {
        assertThatThrownBy(() -> service().simulate("token",
                new CostSimulationRequest("2026-09", null, BigDecimal.ZERO)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0 and 100");
        assertThatThrownBy(() -> service().simulate("token",
                new CostSimulationRequest("2026-09", null, new BigDecimal("120"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0 and 100");
    }

    private CostSimulatorService service() {
        when(sessionService.getSession("token")).thenReturn(new AwsConnectionSessionService.ConnectionSession(
                "access", "secret", "us-east-1", ACCOUNT_ID, Instant.now().plusSeconds(600)));
        return new CostSimulatorService(sessionService, new AwsBillingSourceResolver(billingRepository), recommendationRepository);
    }

    private void stubBilling(List<AwsBilling> rows) {
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(ACCOUNT_ID, "CUR_2_0"))
                .thenReturn(rows);
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(ACCOUNT_ID, "COST_EXPLORER"))
                .thenReturn(List.of());
    }

    private AwsBilling billingRow(LocalDate date, String amount) {
        return new AwsBilling(ACCOUNT_ID, date, date, "Amazon EC2", "us-east-1", BigDecimal.ONE,
                new BigDecimal(amount), "USD", false, "Amazon EC2", "UnblendedCost", null,
                "CUR_2_0", date, date, "cur-" + date, "CUR_2_0");
    }

    private AwsBilling billingRowWithResource(LocalDate date, String amount, String service, String resourceId) {
        return new AwsBilling(ACCOUNT_ID, date, date, service, "us-east-1", BigDecimal.ONE,
                new BigDecimal(amount), "USD", false, service, "UnblendedCost", resourceId,
                "CUR_2_0", date, date, "cur-" + service + "-" + resourceId + "-" + date, "CUR_2_0");
    }

    private AwsRecommendation recommendation(String service, String resourceId, String savings) {
                return recommendation(service, resourceId, savings, "New");
        }

        private AwsRecommendation recommendation(String service, String resourceId, String savings, String status) {
        return new AwsRecommendation(ACCOUNT_ID, "rec-" + resourceId, "Review " + service, "OPTIMIZATION_OPPORTUNITY",
                "HIGH", service, resourceId, "us-east-1", new BigDecimal("100.00"), "stored billing evidence",
                "Use the existing recommendation", "Review resource", savings == null ? null : new BigDecimal(savings),
                                status, "2026-09");
    }
}