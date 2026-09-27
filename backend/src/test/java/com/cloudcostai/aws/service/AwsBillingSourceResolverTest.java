package com.cloudcostai.aws.service;

import com.cloudcostai.aws.model.AwsBilling;
import com.cloudcostai.aws.repository.AwsBillingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AwsBillingSourceResolverTest {
    @Mock private AwsBillingRepository billingRepository;

    @Test
    void resolvesDashboardBillingOnlyFromCurRows() {
        String accountId = "123456789012";
        YearMonth month = YearMonth.of(2026, 8);
        AwsBilling curRow = new AwsBilling(accountId, month.atDay(2), month.atDay(3), "Amazon EC2", "us-east-1",
                BigDecimal.ONE, new BigDecimal("12.50"), "USD", false, "Amazon EC2", "Usage", null,
                "Usage", month.atDay(2), month.atDay(3), "cur-row", "CUR_2_0");
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(accountId, "CUR_2_0"))
                .thenReturn(List.of(curRow));

        var result = new AwsBillingSourceResolver(billingRepository).resolve(accountId, month);

        assertThat(result.dataSource()).isEqualTo("CUR_2_0");
        assertThat(result.rows()).containsExactly(curRow);
        verify(billingRepository, never()).findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(accountId, "COST_EXPLORER");
    }

    @Test
    void displayUsesFetchedLiveMonthAndRetainsCurForOtherMonths() {
        String accountId = "123456789012";
        AwsBilling augustCur = row(accountId, LocalDate.of(2026, 8, 10), "10.00", "CUR EC2", "CUR_2_0");
        AwsBilling septemberCur = row(accountId, LocalDate.of(2026, 9, 10), "20.00", "CUR S3", "CUR_2_0");
        AwsBilling septemberLive = row(accountId, LocalDate.of(2026, 9, 10), "12.50", "Live EC2", "COST_EXPLORER");
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(accountId, "CUR_2_0"))
                .thenReturn(List.of(augustCur, septemberCur));
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(accountId, "COST_EXPLORER"))
                .thenReturn(List.of(septemberLive));

        var result = new AwsBillingSourceResolver(billingRepository).resolveForDisplay(accountId,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 10, 1));

        assertThat(result.dataSource()).isEqualTo("MIXED");
        assertThat(result.rows()).containsExactly(augustCur, septemberLive);
    }

    private AwsBilling row(String accountId, LocalDate date, String cost, String service, String source) {
        return new AwsBilling(accountId, date, date, service, "us-east-1", BigDecimal.ONE,
                new BigDecimal(cost), "USD", false, service, "Usage", null, source,
                date, date, source + date + service, source);
    }
}