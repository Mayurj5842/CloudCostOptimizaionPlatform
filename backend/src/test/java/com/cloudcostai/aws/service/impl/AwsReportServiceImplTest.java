package com.cloudcostai.aws.service.impl;

import com.cloudcostai.aws.config.AwsBillingSyncProperties;
import com.cloudcostai.aws.model.AwsBilling;
import com.cloudcostai.aws.repository.AwsBillingRepository;
import com.cloudcostai.aws.service.AwsBillingSourceResolver;
import com.cloudcostai.aws.service.AwsConnectionSessionService;
import com.cloudcostai.aws.service.AwsInsightService;
import com.cloudcostai.aws.service.AwsRecommendationService;
import com.cloudcostai.aws.service.AwsResourceService;
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
class AwsReportServiceImplTest {
    @Mock private AwsConnectionSessionService sessionService;
    @Mock private AwsBillingRepository billingRepository;
    @Mock private AwsBillingSyncProperties billingProperties;
    @Mock private AwsInsightService insightService;
    @Mock private AwsRecommendationService recommendationService;
    @Mock private AwsResourceService resourceService;

    @Test
    void augustReportIncludesAugustThirtyFirstAndExcludesSeptemberFirst() {
        String account = "123456789012";
        when(sessionService.getSession("token")).thenReturn(new AwsConnectionSessionService.ConnectionSession("access", "secret", "ap-south-1", account, Instant.now().plusSeconds(600)));
        when(billingRepository.findByAwsAccountId(account)).thenReturn(List.of(
                row(account, "2026-08-01", "10.00", "august-first"),
                row(account, "2026-08-31", "20.00", "august-last"),
                row(account, "2026-09-01", "1000.00", "september")));
        when(resourceService.discoverResources("token")).thenThrow(new IllegalStateException("resource unavailable"));
        AwsReportServiceImpl service = new AwsReportServiceImpl(sessionService, billingRepository, billingProperties, insightService, recommendationService, resourceService);

        var response = service.getReport("token", "2026-08");

        assertThat(response.status()).isEqualTo("PARTIAL");
        assertThat(response.billingMonth()).isEqualTo("2026-08");
        assertThat(response.summary().totalCost()).isEqualByComparingTo("30.00");
        assertThat(response.costTrend()).extracting(item -> item.cost()).containsExactly(new BigDecimal("10.00"), new BigDecimal("20.00"));
    }

    @Test
    void emptyMonthReturnsNoDataWithoutZeroSummary() {
        String account = "123456789012";
        when(sessionService.getSession("token")).thenReturn(new AwsConnectionSessionService.ConnectionSession("access", "secret", "ap-south-1", account, Instant.now().plusSeconds(600)));
        when(billingRepository.findByAwsAccountId(account)).thenReturn(List.of(row(account, "2026-09-01", "10.00", "september")));
        AwsReportServiceImpl service = new AwsReportServiceImpl(sessionService, billingRepository, billingProperties, insightService, recommendationService, resourceService);

        var response = service.getReport("token", "2026-08");

        assertThat(response.status()).isEqualTo("NO_DATA");
        assertThat(response.summary().totalCost()).isNull();
    }

        @Test
        void reportUsesPersistedCostExplorerRowsForTheSelectedMonth() {
        String account = "123456789012";
        when(sessionService.getSession("token")).thenReturn(new AwsConnectionSessionService.ConnectionSession(
            "access", "secret", "ap-south-1", account, Instant.now().plusSeconds(600)));
        AwsBilling septemberCur = row(account, "2026-09-10", "20.00", "september-cur");
        AwsBilling septemberLive = sourcedRow(account, "2026-09-10", "12.50", "COST_EXPLORER");
        AwsBilling augustCur = row(account, "2026-08-10", "8.00", "august-cur");
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(account, "CUR_2_0"))
            .thenReturn(List.of(augustCur, septemberCur));
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(account, "COST_EXPLORER"))
            .thenReturn(List.of(septemberLive));

        AwsReportServiceImpl service = new AwsReportServiceImpl(sessionService, billingRepository, billingProperties,
            insightService, recommendationService, resourceService, null,
            new AwsBillingSourceResolver(billingRepository));
        var response = service.getReport("token", "2026-09");

        assertThat(response.dataSource()).isEqualTo("COST_EXPLORER");
        assertThat(response.summary().totalCost()).isEqualByComparingTo("12.50");
        assertThat(response.summary().previousMonthCost()).isEqualByComparingTo("8.00");
        assertThat(response.serviceBreakdown()).extracting("cost")
            .containsExactly(new BigDecimal("12.50"));
        }

    private AwsBilling row(String account, String date, String cost, String key) {
        LocalDate usage = LocalDate.parse(date);
        return new AwsBilling(account, usage, usage.plusDays(1), "Amazon EC2", "ap-south-1", BigDecimal.ONE, new BigDecimal(cost), "USD", false, "Amazon EC2", "Usage", key, "Usage", usage, usage.plusDays(1), key);
    }

    private AwsBilling sourcedRow(String account, String date, String cost, String source) {
        LocalDate usage = LocalDate.parse(date);
        return new AwsBilling(account, usage, usage, "Amazon EC2", "ap-south-1", BigDecimal.ONE,
                new BigDecimal(cost), "USD", false, "Amazon EC2", "Usage", null, "Usage",
                usage, usage, source + usage, source);
    }
}
