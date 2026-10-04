package com.cloudcostai.billing.service;

import com.cloudcostai.aws.model.AwsBilling;
import com.cloudcostai.aws.repository.AwsBillingRepository;
import com.cloudcostai.aws.service.AwsBillingSourceResolver;
import com.cloudcostai.billing.dto.BudgetRequest;
import com.cloudcostai.billing.model.Budget;
import com.cloudcostai.billing.model.BudgetAlert;
import com.cloudcostai.billing.repository.BudgetAlertRepository;
import com.cloudcostai.billing.repository.BudgetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetServiceAdvancedTest {
    @Mock private BudgetRepository budgetRepository;
    @Mock private BudgetAlertRepository alertRepository;
    @Mock private AwsBillingRepository billingRepository;

    @Test
    void forecastProjectsMonthEndOnlyWithEnoughCurrentData() {
        var month = YearMonth.of(2026, 9);
        var forecast = BudgetService.calculateForecast(new BigDecimal("75.00"), new BigDecimal("100.00"),
                3, month, LocalDate.of(2026, 9, 15));
        var sparse = BudgetService.calculateForecast(new BigDecimal("75.00"), new BigDecimal("100.00"),
                2, month, LocalDate.of(2026, 9, 15));
        var missing = BudgetService.calculateForecast(null, new BigDecimal("100.00"),
                0, month, LocalDate.of(2026, 9, 15));

        assertThat(forecast.available()).isTrue();
        assertThat(forecast.amount()).isEqualByComparingTo("150.00");
        assertThat(forecast.variance()).isEqualByComparingTo("50.00");
        assertThat(forecast.message()).contains("Estimate");
        assertThat(sparse.available()).isFalse();
        assertThat(sparse.amount()).isNull();
        assertThat(sparse.message()).contains("Insufficient");
        assertThat(missing.available()).isFalse();
        assertThat(missing.message()).contains("No billing data");
    }

        @Test
        void createsConfiguredThresholdAlertsWithConsistentSeverity() {
                String accountId = "123456789012";
                YearMonth month = YearMonth.now().minusMonths(1);
                stubBillingRows(accountId, List.of(billingRow(accountId, month, 1, "750.00")));
                Budget budget = new Budget("system-user", accountId, "Monthly", new BigDecimal("1000.00"), "USD",
                                "MONTHLY", month.atDay(1), month.atEndOfMonth(), "ACTIVE", List.of(50, 75, 90, 100));
                when(alertRepository.existsByBudgetIdAndThresholdPercentAndAlertPeriod(any(), any(), any())).thenReturn(false);
                when(alertRepository.existsByEventKey(anyString())).thenReturn(false);

                service().evaluateAlerts(budget, month, false);

                ArgumentCaptor<BudgetAlert> alerts = ArgumentCaptor.forClass(BudgetAlert.class);
                verify(alertRepository, times(2)).save(alerts.capture());
                assertThat(alerts.getAllValues()).extracting(BudgetAlert::getThresholdPercent).containsExactly(50, 75);
                assertThat(alerts.getAllValues()).extracting(BudgetAlert::getSeverity).containsExactly("INFO", "WARNING");
        }

    @Test
    void createsUpdatesAndDeletesBudgetWithoutDeletingAlertHistory() {
        String accountId = "123456789012";
        String month = YearMonth.now().minusMonths(1).toString();
        stubBillingRows(accountId, List.of());
        when(budgetRepository.existsByAwsAccountIdAndName(accountId, "EC2 budget")).thenReturn(false);
        BudgetService service = service();

        var created = service.createBudget("system-user", request("EC2 budget", accountId, "100.00", month,
                "Amazon EC2", true, 65));
        ArgumentCaptor<Budget> savedBudget = ArgumentCaptor.forClass(Budget.class);
        verify(budgetRepository).save(savedBudget.capture());
        Budget budget = savedBudget.getValue();
        when(budgetRepository.findById(created.id())).thenReturn(Optional.of(budget));

        var updated = service.updateBudget(created.id(), accountId,
                request("Compute budget", accountId, "250.00", month, "Amazon EC2", false, 80));
        assertThat(updated.name()).isEqualTo("Compute budget");
        assertThat(updated.amount()).isEqualByComparingTo("250.00");
        assertThat(updated.enabled()).isFalse();
        assertThat(updated.spikeThresholdPercent()).isEqualTo(80);

        service.deleteBudget(created.id(), accountId);
        verify(budgetRepository).delete(budget);
        verify(alertRepository, never()).delete(any());
    }

    @Test
    void spendingSpikeRequiresTwoPeriodsAndDeduplicatesTheEvent() {
        String accountId = "123456789012";
        YearMonth month = YearMonth.now().minusMonths(1);
        List<AwsBilling> rows = List.of(
                billingRow(accountId, month.minusMonths(1), 1, "40.00"),
                billingRow(accountId, month.minusMonths(1), 2, "40.00"),
                billingRow(accountId, month.minusMonths(1), 3, "40.00"),
                billingRow(accountId, month, 1, "100.00"),
                billingRow(accountId, month, 2, "100.00"),
                billingRow(accountId, month, 3, "100.00"),
                new AwsBilling(accountId, month.minusMonths(1).atDay(1), month.minusMonths(1).atEndOfMonth(),
                        "Amazon S3", "us-east-1", BigDecimal.ONE, new BigDecimal("1000.00"), "USD", false,
                        "Amazon S3", "Usage", null, "Usage", month.minusMonths(1).atDay(1),
                        month.minusMonths(1).atDay(1), "s3-prior-" + month, "CUR_2_0"),
                new AwsBilling(accountId, month.atDay(1), month.atEndOfMonth(), "Amazon S3", "us-east-1",
                        BigDecimal.ONE, new BigDecimal("1000.00"), "USD", false, "Amazon S3", "Usage", null,
                        "Usage", month.atDay(1), month.atDay(1), "s3-current-" + month, "CUR_2_0"));
        stubBillingRows(accountId, rows);
        Budget budget = new Budget("system-user", accountId, "EC2 budget", new BigDecimal("1000.00"), "USD",
                "MONTHLY", month.atDay(1), month.atEndOfMonth(), "ACTIVE", List.of(100), "Amazon EC2", true, 50);
        when(budgetRepository.findByAwsAccountIdOrderByCreatedAtDesc(accountId)).thenReturn(List.of(budget));
        when(alertRepository.existsByEventKey(anyString())).thenReturn(false, true);
        BudgetService service = service();

        var result = service.getBudgetsForAccount(accountId, month.toString()).get(0);
        assertThat(result.actualSpend()).isEqualByComparingTo("300.00");
        service.getBudgetsForAccount(accountId, month.toString());

        ArgumentCaptor<BudgetAlert> savedAlert = ArgumentCaptor.forClass(BudgetAlert.class);
        verify(alertRepository, times(1)).save(savedAlert.capture());
        assertThat(savedAlert.getValue().getAlertType()).isEqualTo("SPENDING_SPIKE");
        assertThat(savedAlert.getValue().getSeverity()).isEqualTo("WARNING");
        assertThat(savedAlert.getValue().getPercentageChange()).isEqualByComparingTo("150.00");
        assertThat(savedAlert.getValue().getComparisonPeriod()).contains(month.minusMonths(1).toString());
    }

    @Test
    void filtersPersistedAlertHistoryBySeverityTypeAndDate() {
        String accountId = "123456789012";
        YearMonth month = YearMonth.now();
        LocalDate today = LocalDate.now();
        BudgetAlert critical = new BudgetAlert(null, 90, month.toString(), "UNREAD", "Reached threshold",
                "BUDGET_THRESHOLD", "CRITICAL", "Budget threshold reached", "Production", accountId,
                "Amazon EC2", new BigDecimal("90.00"), new BigDecimal("100.00"), "USD", null, null,
                "budget-event");
        BudgetAlert info = new BudgetAlert(null, 50, month.toString(), "UNREAD", "Lower threshold",
                "BUDGET_THRESHOLD", "INFO", "Budget threshold reached", "Production", accountId,
                "Amazon EC2", new BigDecimal("50.00"), new BigDecimal("100.00"), "USD", null, null,
                "info-event");
        when(budgetRepository.findByAwsAccountId(accountId)).thenReturn(List.of());
        when(alertRepository.findByAwsAccountIdOrderByTriggeredAtDesc(accountId)).thenReturn(List.of(critical, info));

        var results = service().getAlertsForAccount(accountId, month.toString(), "CRITICAL", "BUDGET_THRESHOLD",
                today, today);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).severity()).isEqualTo("CRITICAL");
        assertThat(results.get(0).budgetName()).isEqualTo("Production");
        assertThat(results.get(0).actualSpend()).isEqualByComparingTo("90.00");
    }

    private BudgetService service() {
        return new BudgetService(budgetRepository, alertRepository, billingRepository,
                new AwsBillingSourceResolver(billingRepository));
    }

    private BudgetRequest request(String name, String accountId, String amount, String month,
                                  String serviceName, boolean enabled, int spikeThreshold) {
        return new BudgetRequest(name, accountId, new BigDecimal(amount), "USD", "MONTHLY",
                List.of(50, 75, 90, 100), month, serviceName, enabled, spikeThreshold);
    }

    private void stubBillingRows(String accountId, List<AwsBilling> rows) {
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(accountId, "CUR_2_0"))
                .thenReturn(rows);
        when(billingRepository.findByAwsAccountIdAndDataSourceOrderByBillingPeriodStartAsc(accountId, "COST_EXPLORER"))
                .thenReturn(List.of());
    }

    private AwsBilling billingRow(String accountId, YearMonth month, int day, String amount) {
        LocalDate date = month.atDay(day);
        return new AwsBilling(accountId, month.atDay(1), month.atEndOfMonth(), "Amazon EC2", "us-east-1",
                BigDecimal.ONE, new BigDecimal(amount), "USD", false, "Amazon EC2", "Usage", null,
                "Usage", date, date, "cur-" + month + "-" + day, "CUR_2_0");
    }
}