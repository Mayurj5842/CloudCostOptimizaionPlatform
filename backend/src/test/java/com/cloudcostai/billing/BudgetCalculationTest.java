package com.cloudcostai.billing;

import com.cloudcostai.billing.service.BudgetService;
import com.cloudcostai.aws.model.AwsBilling;
import com.cloudcostai.aws.repository.AwsBillingRepository;
import com.cloudcostai.aws.service.AwsBillingSourceResolver;
import com.cloudcostai.billing.model.Budget;
import com.cloudcostai.billing.repository.BudgetAlertRepository;
import com.cloudcostai.billing.repository.BudgetRepository;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class BudgetCalculationTest {
    @Mock private BudgetRepository budgetRepository;
    @Mock private BudgetAlertRepository budgetAlertRepository;
    @Mock private AwsBillingRepository billingRepository;

    @Test
    void calculatesUsagePercentageAndStatusFromRealSpend() {
        BigDecimal budgetAmount = new BigDecimal("1000.00");
        BigDecimal actualSpend = new BigDecimal("620.00");

        BigDecimal usage = actualSpend.divide(budgetAmount, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        assertThat(usage).isEqualByComparingTo(new BigDecimal("62.0000"));
        assertThat(BudgetService.calculateStatus(usage)).isEqualTo("NORMAL");
        assertThat(actualSpend.compareTo(budgetAmount)).isLessThan(0);
    }

        @Test
        void selectedMonthBudgetAndAlertsUsePersistedLiveSpend() {
        String accountId = "123456789012";
        LocalDate usageDate = LocalDate.of(2026, 9, 10);
        AwsBilling cur = billingRow(accountId, usageDate, "20.00", "CUR_2_0");
        AwsBilling live = billingRow(accountId, usageDate, "12.50", "COST_EXPLORER");
        Budget budget = new Budget("user", accountId, "September", new BigDecimal("10.00"), "USD", "MONTHLY",
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), "ACTIVE", List.of(80, 100));
        when(budgetRepository.findByAwsAccountIdOrderByCreatedAtDesc(accountId)).thenReturn(List.of(budget));
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(accountId, "CUR_2_0"))
            .thenReturn(List.of(cur));
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(accountId, "COST_EXPLORER"))
            .thenReturn(List.of(live));
        when(budgetAlertRepository.existsByBudgetIdAndThresholdPercentAndAlertPeriod(any(), any(), any())).thenReturn(false);

        BudgetService service = new BudgetService(budgetRepository, budgetAlertRepository, billingRepository,
            new AwsBillingSourceResolver(billingRepository));
        var result = service.getBudgetsForAccount(accountId, "2026-09").get(0);

        assertThat(result.actualSpend()).isEqualByComparingTo("12.50");
        assertThat(result.usagePercentage()).isEqualByComparingTo("125.0000");
        assertThat(result.status()).isEqualTo("EXCEEDED");
        verify(budgetAlertRepository, org.mockito.Mockito.times(2)).save(any());
        }

        private AwsBilling billingRow(String accountId, LocalDate date, String amount, String source) {
        return new AwsBilling(accountId, date, date, "Amazon EC2", "us-east-1", BigDecimal.ONE,
            new BigDecimal(amount), "USD", false, "Amazon EC2", "Usage", null, "Usage",
            date, date, source + date, source);
        }
}
