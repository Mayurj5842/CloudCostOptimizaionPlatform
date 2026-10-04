package com.cloudcostai.aws.service.impl;

import com.cloudcostai.aws.model.AwsBilling;
import com.cloudcostai.aws.repository.AwsBillingRepository;
import com.cloudcostai.aws.repository.AwsRecommendationRepository;
import com.cloudcostai.aws.service.AwsBillingSourceResolver;
import com.cloudcostai.aws.service.AwsConnectionSessionService;
import com.cloudcostai.billing.repository.BudgetRepository;
import com.cloudcostai.billing.model.Budget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AwsCostAnalyticsServiceImplTest {
    @Mock private AwsConnectionSessionService sessionService;
    @Mock private AwsBillingRepository billingRepository;
    @Mock private AwsRecommendationRepository recommendationRepository;
    @Mock private BudgetRepository budgetRepository;

    @Test
    void selectedAugustUsesExclusiveMonthBoundaryAndPreviousComparison() {
        String account = "123456789012";
        when(sessionService.getSession("token")).thenReturn(session(account));
        when(billingRepository.findByAwsAccountId(account)).thenReturn(List.of(
                row(account, LocalDate.of(2026, 7, 31), "5.00", "July"),
                row(account, LocalDate.of(2026, 8, 1), "10.00", "EC2"),
                row(account, LocalDate.of(2026, 8, 31), "20.00", "S3"),
                row(account, LocalDate.of(2026, 9, 1), "100.00", "RDS")));
        when(recommendationRepository.findByAwsAccountIdOrderByCreatedAtDesc(account)).thenReturn(List.of());
        when(budgetRepository.findFirstByAwsAccountIdAndStatusOrderByCreatedAtDesc(account, "ACTIVE")).thenReturn(Optional.empty());

        var response = service().getAnalytics("token", "CURRENT_MONTH", "2026-08");

        assertThat(response.dataStatus()).isEqualTo("AVAILABLE");
        assertThat(response.summary().totalCost()).isEqualByComparingTo("30.00");
        assertThat(response.summary().previousPeriodCost()).isEqualByComparingTo("5.00");
        assertThat(response.summary().difference()).isEqualByComparingTo("25.00");
        assertThat(response.summary().percentageChange()).isEqualByComparingTo("500.00");
        assertThat(response.period().start()).isEqualTo("2026-08-01");
        assertThat(response.period().end()).isEqualTo("2026-08-31");
        assertThat(response.serviceCosts().stream().map(item -> item.cost()).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("30.00");
        assertThat(response.dailyTrend().stream().map(item -> item.cost()).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("30.00");
    }

    @Test
    void anomalyEntriesUseEachFlaggedDayAndItsOwnCost() {
        String account = "123456789012";
        when(sessionService.getSession("token")).thenReturn(session(account));
        when(billingRepository.findByAwsAccountId(account)).thenReturn(List.of(
            row(account, LocalDate.of(2026, 8, 1), "1000.00", "EC2"),
            row(account, LocalDate.of(2026, 8, 2), "900.00", "RDS"),
            row(account, LocalDate.of(2026, 8, 3), "1.00", "S3"),
            row(account, LocalDate.of(2026, 8, 4), "1.00", "S3"),
            row(account, LocalDate.of(2026, 8, 5), "1.00", "S3"),
            row(account, LocalDate.of(2026, 8, 6), "1.00", "S3"),
            row(account, LocalDate.of(2026, 8, 7), "1.00", "S3"),
            row(account, LocalDate.of(2026, 8, 8), "0.00", "S3")));
        when(recommendationRepository.findByAwsAccountIdOrderByCreatedAtDesc(account)).thenReturn(List.of());
        when(budgetRepository.findFirstByAwsAccountIdAndStatusOrderByCreatedAtDesc(account, "ACTIVE")).thenReturn(Optional.empty());

        var response = service().getAnalytics("token", "CURRENT_MONTH", "2026-08");

        assertThat(response.anomalies()).extracting("date", "cost")
            .containsExactlyInAnyOrder(
                tuple("2026-08-01", new BigDecimal("1000.00")),
                tuple("2026-08-02", new BigDecimal("900.00")));
    }

    @Test
    void nullUsageDateFallsBackToBillingPeriodStart() {
        String account = "123456789012";
        when(sessionService.getSession("token")).thenReturn(session(account));
        when(billingRepository.findByAwsAccountId(account)).thenReturn(List.of(rowWithNullUsageDate(account, LocalDate.of(2026, 9, 10), "12.50")));
        when(recommendationRepository.findByAwsAccountIdOrderByCreatedAtDesc(account)).thenReturn(List.of());
        when(budgetRepository.findFirstByAwsAccountIdAndStatusOrderByCreatedAtDesc(account, "ACTIVE")).thenReturn(Optional.empty());

        var response = service().getAnalytics("token", "CURRENT_MONTH", "2026-09");

        assertThat(response.dataStatus()).isEqualTo("AVAILABLE");
        assertThat(response.summary().totalCost()).isEqualByComparingTo("12.50");
    }

    @Test
    void noSelectedMonthDataDoesNotBecomeZero() {
        String account = "123456789012";
        when(sessionService.getSession("token")).thenReturn(session(account));
        when(billingRepository.findByAwsAccountId(account)).thenReturn(List.of(row(account, LocalDate.of(2026, 8, 1), "12.50", "EC2")));
        when(recommendationRepository.findByAwsAccountIdOrderByCreatedAtDesc(account)).thenReturn(List.of());
        when(budgetRepository.findFirstByAwsAccountIdAndStatusOrderByCreatedAtDesc(account, "ACTIVE")).thenReturn(Optional.empty());

        var response = service().getAnalytics("token", "CURRENT_MONTH", "2026-09");

        assertThat(response.dataStatus()).isEqualTo("NO_CUR_DATA");
        assertThat(response.summary().totalCost()).isNull();
        assertThat(response.message()).contains("No billing data");
    }

        @Test
        void selectedMonthAnalyticsUsesSavedCostExplorerRowsInsteadOfCur() {
        String account = "123456789012";
        AwsBilling augustCur = sourcedRow(account, LocalDate.of(2026, 8, 10), "10.00", "CUR S3", "CUR_2_0");
        AwsBilling septemberCur = sourcedRow(account, LocalDate.of(2026, 9, 10), "20.00", "CUR EC2", "CUR_2_0");
        AwsBilling septemberLive = sourcedRow(account, LocalDate.of(2026, 9, 10), "12.50", "Live EC2", "COST_EXPLORER");
        when(sessionService.getSession("token")).thenReturn(session(account));
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(account, "CUR_2_0"))
            .thenReturn(List.of(augustCur, septemberCur));
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(account, "COST_EXPLORER"))
            .thenReturn(List.of(septemberLive));
        when(recommendationRepository.findByAwsAccountIdOrderByCreatedAtDesc(account)).thenReturn(List.of());
        when(budgetRepository.findFirstByAwsAccountIdAndStatusOrderByCreatedAtDesc(account, "ACTIVE")).thenReturn(Optional.empty());

        var service = new AwsCostAnalyticsServiceImpl(sessionService, billingRepository, recommendationRepository,
            budgetRepository, new AwsBillingSourceResolver(billingRepository));
        var response = service.getAnalytics("token", "CURRENT_MONTH", "2026-09");

        assertThat(response.dataSource()).isEqualTo("COST_EXPLORER");
        assertThat(response.summary().totalCost()).isEqualByComparingTo("12.50");
        assertThat(response.summary().previousPeriodCost()).isEqualByComparingTo("10.00");
        assertThat(response.serviceCosts()).extracting("name", "cost")
            .containsExactly(tuple("Live EC2", new BigDecimal("12.50")));
        }

    @Test
    void budgetUsesSelectedMonthSpend() {
        String account = "123456789012";
        when(sessionService.getSession("token")).thenReturn(session(account));
        when(billingRepository.findByAwsAccountId(account)).thenReturn(List.of(row(account, LocalDate.of(2026, 8, 10), "25.00", "EC2")));
        when(recommendationRepository.findByAwsAccountIdOrderByCreatedAtDesc(account)).thenReturn(List.of());
        Budget configured = new Budget("user", account, "August", new BigDecimal("100.00"), "USD", "MONTHLY", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), "ACTIVE", Arrays.asList(50, 80, 100));
        when(budgetRepository.findFirstByAwsAccountIdAndStatusOrderByCreatedAtDesc(account, "ACTIVE")).thenReturn(Optional.of(configured));

        var response = service().getAnalytics("token", "CURRENT_MONTH", "2026-08");

        assertThat(response.budget().configured()).isTrue();
        assertThat(response.budget().actualSpend()).isEqualByComparingTo("25.00");
        assertThat(response.budget().usagePercentage()).isEqualByComparingTo("25.00");
    }

    private AwsCostAnalyticsServiceImpl service() {
        return new AwsCostAnalyticsServiceImpl(sessionService, billingRepository, recommendationRepository, budgetRepository);
    }

    private AwsConnectionSessionService.ConnectionSession session(String account) {
        return new AwsConnectionSessionService.ConnectionSession("access", "secret", "us-east-1", account, Instant.now().plusSeconds(300));
    }

    private AwsBilling row(String account, LocalDate date, String cost, String service) {
        return new AwsBilling(account, date, date.plusDays(1), service, "us-east-1", BigDecimal.ONE,
                new BigDecimal(cost), "USD", false, service, "Usage", "resource", "Usage", date, date.plusDays(1), account + date + service);
    }

    private AwsBilling rowWithNullUsageDate(String account, LocalDate billingDate, String cost) {
        return new AwsBilling(account, billingDate, billingDate.plusDays(1), "Amazon EC2", null, BigDecimal.ONE,
                new BigDecimal(cost), "USD", false, "Amazon EC2", "Usage", "resource", "Usage", null, null, account + billingDate);
    }

    private AwsBilling sourcedRow(String account, LocalDate date, String cost, String service, String source) {
        return new AwsBilling(account, date, date, service, "us-east-1", BigDecimal.ONE, new BigDecimal(cost),
                "USD", false, service, "Usage", null, "Usage", date, date, source + date + service, source);
    }
}
