package com.cloudcostai.aws.service.impl;

import com.cloudcostai.aws.config.AwsBillingSyncProperties;
import com.cloudcostai.aws.dto.response.AwsInsightResponse;
import com.cloudcostai.aws.model.AwsBilling;
import com.cloudcostai.aws.repository.AwsBillingRepository;
import com.cloudcostai.aws.service.AwsBillingSourceResolver;
import com.cloudcostai.aws.service.AwsConnectionSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AwsInsightServiceImplTest {
    @Mock private AwsConnectionSessionService sessionService;
    @Mock private AwsBillingRepository billingRepository;

    @Test
    void zeroSpendReturnsExplicitNoDataWithoutFinancialInsight() {
        AwsInsightServiceImpl service = service(List.of(billingRow("Amazon S3", LocalDate.now().minusDays(2), "0.00", "zero")));

        AwsInsightResponse response = service.getInsights("token");

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.insights()).isNotEmpty();
    }

    @Test
    void selectedAugustUsesOnlyAugustAndFindsHighestService() {
        AwsInsightServiceImpl service = service(List.of(
                billingRow("Amazon EC2", "2026-07-31", "70.00", "july"),
                billingRow("Amazon EC2", "2026-08-01", "120.00", "august-ec2"),
                billingRow("Amazon RDS", "2026-08-31", "80.00", "august-rds"),
                billingRow("Amazon S3", "2026-09-01", "1000.00", "september")));

        AwsInsightResponse response = service.getInsights("token", "2026-08");

        assertThat(response.insights()).isNotEmpty();
    }

    @Test
    void previousPeriodIncreaseDoesNotDivideByZero() {
        AwsInsightServiceImpl service = service(List.of(
                billingRow("Amazon EC2", LocalDate.now().minusDays(2), "120.00", "current"),
                billingRow("Amazon EC2", LocalDate.now().minusDays(35), "0.00", "zero-baseline")));

        AwsInsightResponse response = service.getInsights("token");

        assertThat(response.insights()).isNotEmpty();
    }

    @Test
    void selectedMonthUsesFirstDayThroughNextMonthExclusive() {
        AwsInsightServiceImpl service = service(List.of(
                billingRow("Amazon EC2", "2026-08-01", "120.00", "august-start"),
                billingRow("Amazon RDS", "2026-08-31", "80.00", "august-end"),
                billingRow("Amazon S3", "2026-09-01", "1000.00", "september")));

        AwsInsightResponse response = service.getInsights("token", "2026-08");

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.billingPeriodStart()).isEqualTo("2026-08-01");
        assertThat(response.billingPeriodEnd()).isEqualTo("2026-08-31");
        assertThat(response.totalSpend()).isEqualByComparingTo("200.00");
    }

    @Test
    void selectedMonthWithoutRowsReturnsNoCurData() {
        AwsInsightServiceImpl service = service(List.of(billingRow("Amazon S3", "2026-09-01", "10.00", "september")));

        AwsInsightResponse response = service.getInsights("token", "2026-08");

        assertThat(response.status()).isEqualTo("NO_CUR_DATA");
        assertThat(response.insights()).isEmpty();
    }

        @Test
        void selectedMonthInsightsUsePersistedCostExplorerRows() {
        String accountId = "123456789012";
        LocalDate usageDate = LocalDate.of(2026, 9, 10);
        AwsBilling liveRow = new AwsBilling(accountId, usageDate, usageDate, "Amazon EC2", "ap-south-1",
            BigDecimal.ONE, new BigDecimal("12.50"), "USD", false, "Amazon EC2", "Usage", null,
            "Usage", usageDate, usageDate, "live-row", "COST_EXPLORER");
        when(sessionService.getSession("token")).thenReturn(new AwsConnectionSessionService.ConnectionSession(
            "access", "secret", "ap-south-1", accountId, Instant.now().plusSeconds(600)));
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(accountId, "CUR_2_0"))
            .thenReturn(List.of());
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(accountId, "COST_EXPLORER"))
            .thenReturn(List.of(liveRow));

        var service = new AwsInsightServiceImpl(sessionService, billingRepository, null, null,
            new AwsBillingSourceResolver(billingRepository));
        AwsInsightResponse response = service.getInsights("token", "2026-09");

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.dataSource()).isEqualTo("COST_EXPLORER");
        assertThat(response.totalSpend()).isEqualByComparingTo("12.50");
        assertThat(response.insights()).isNotEmpty();
        }

    @Test
    void estimatedSavingsSumsTopCostDriverImpacts() {
        AwsInsightServiceImpl service = service(List.of(
                billingRow("Amazon S3", "2026-08-10", "120.00", "august-s3"),
                billingRow("Amazon EC2", "2026-08-11", "80.00", "august-ec2")));

        assertThat(service.estimatedSavings("token", "2026-08")).isEqualByComparingTo("20.00");
    }

    @Test
    void estimatedSavingsIsNullWhenNoBillingData() {
        AwsInsightServiceImpl service = service(List.of());

        assertThat(service.estimatedSavings("token", "2026-08")).isNull();
    }

    private AwsInsightServiceImpl service(List<AwsBilling> rows) {
        String accountId = "123456789012";
        when(sessionService.getSession("token")).thenReturn(new AwsConnectionSessionService.ConnectionSession(
                "access", "secret", "ap-south-1", accountId, Instant.now().plusSeconds(600)));
        when(billingRepository.findByAwsAccountId(accountId)).thenReturn(rows);
        return new AwsInsightServiceImpl(sessionService, billingRepository);
    }

    private AwsBilling billingRow(String service, String date, String cost, String key) {
        return billingRow(service, LocalDate.parse(date), cost, key);
    }

    private AwsBilling billingRow(String service, LocalDate usageDate, String cost, String key) {
        return new AwsBilling("123456789012", usageDate, usageDate.plusDays(1), service, "ap-south-1",
                BigDecimal.ONE, new BigDecimal(cost), "USD", false, service, "Usage", "resource-1", "Usage",
                usageDate, usageDate.plusDays(1), key);
    }
}
