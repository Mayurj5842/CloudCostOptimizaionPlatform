package com.cloudcostai.aws.service.impl;

import com.cloudcostai.aws.config.AwsClientFactory;
import com.cloudcostai.aws.dto.response.AwsDashboardResponse;
import com.cloudcostai.aws.dto.response.AwsDashboardResponse.BudgetUsage;
import com.cloudcostai.aws.dto.response.AwsDashboardResponse.CostPoint;
import com.cloudcostai.aws.dto.response.AwsDashboardResponse.ServiceCost;
import com.cloudcostai.aws.dto.response.AwsDashboardChartResponse;
import com.cloudcostai.aws.dto.response.AwsResourceResponse;
import com.cloudcostai.aws.service.AwsConnectionSessionService;
import com.cloudcostai.aws.service.AwsDashboardService;
import com.cloudcostai.aws.service.AwsResourceService;
import com.cloudcostai.aws.service.AwsBillingSourceResolver;
import com.cloudcostai.aws.model.AwsBilling;
import com.cloudcostai.aws.repository.AwsBillingRepository;
import com.cloudcostai.aws.repository.AwsRecommendationRepository;
import com.cloudcostai.billing.model.Budget;
import com.cloudcostai.billing.repository.BudgetRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AwsDashboardServiceImpl implements AwsDashboardService {
    private static final String CURRENCY = "USD";
    private final AwsConnectionSessionService sessionService;
    private final AwsResourceService resourceService;
    private final AwsBillingRepository billingRepository;
    private final AwsRecommendationRepository recommendationRepository;
    private final BudgetRepository budgetRepository;
    private final AwsBillingSourceResolver billingSourceResolver;

    public AwsDashboardServiceImpl(AwsClientFactory clientFactory,
                                   AwsConnectionSessionService sessionService,
                                   AwsResourceService resourceService,
                                   com.cloudcostai.aws.config.AwsBillingSyncProperties ignored,
                                   AwsBillingRepository billingRepository) {
        this(sessionService, resourceService, billingRepository, null, null);
    }

    @Autowired
    public AwsDashboardServiceImpl(AwsConnectionSessionService sessionService,
                                   AwsResourceService resourceService,
                                   AwsBillingRepository billingRepository,
                                   AwsRecommendationRepository recommendationRepository,
                                   BudgetRepository budgetRepository,
                                   AwsBillingSourceResolver billingSourceResolver) {
        this.sessionService = sessionService;
        this.resourceService = resourceService;
        this.billingRepository = billingRepository;
        this.recommendationRepository = recommendationRepository;
        this.budgetRepository = budgetRepository;
        this.billingSourceResolver = billingSourceResolver;
    }

    public AwsDashboardServiceImpl(AwsConnectionSessionService sessionService,
                                   AwsResourceService resourceService,
                                   AwsBillingRepository billingRepository,
                                   AwsRecommendationRepository recommendationRepository,
                                   BudgetRepository budgetRepository) {
        this(sessionService, resourceService, billingRepository, recommendationRepository, budgetRepository, null);
    }

    public AwsDashboardResponse getDashboard(String connectionToken) {
        return getDashboard(connectionToken, null);
    }

    @Override
    public AwsDashboardResponse getDashboard(String connectionToken, String month) {
        AwsConnectionSessionService.ConnectionSession session = sessionService.getSession(connectionToken);
        AwsResourceResponse resources = resourceService.discoverResources(connectionToken);
        return databaseDashboard(session.accountId(), resources, selectedMonth(month));
    }

    @Override
    public AwsDashboardChartResponse getCostTrend(String connectionToken, String range, String month) {
        var session = sessionService.getSession(connectionToken);
        ChartRange selected = chartRange(range, selectedMonth(month));
        AwsBillingSourceResolver.ResolvedBilling resolved = billingSourceResolver == null ? null
            : billingSourceResolver.resolve(session.accountId(), selected.start(), selected.end());
        List<AwsBilling> billingRows = resolved == null
            ? sourceRows(session.accountId(), selectedMonth(month), selected.start(), selected.end()) : resolved.rows();
        Map<LocalDate, BigDecimal> grouped = billingRows.stream().collect(Collectors.groupingBy(this::billingDate,
            Collectors.reducing(BigDecimal.ZERO, row -> row.getCostAmount() == null ? BigDecimal.ZERO : row.getCostAmount(), BigDecimal::add)));
        BigDecimal total = grouped.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        String dataSource = resolved == null ? "CUR_2_0" : resolved.dataSource();
        String currency = billingRows.stream().map(AwsBilling::getCurrency).filter(value -> value != null && !value.isBlank())
            .findFirst().orElseGet(() -> currency(session.accountId()));
        return new AwsDashboardChartResponse(session.accountId(), currency, selected.name(),
            grouped.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(row -> new AwsDashboardChartResponse.CostPoint(row.getKey().toString(), money(row.getValue()))).toList(),
            List.of(), billingRows.isEmpty() ? null : money(total), billingRows.isEmpty() ? "NO_BILLING_DATA" : "AVAILABLE", dataSource);
    }

    @Override
    public AwsDashboardChartResponse getServiceDistribution(String connectionToken, String range, String month) {
        var session = sessionService.getSession(connectionToken);
        YearMonth selectedMonth = selectedMonth(month);
        ChartRange selected = chartRange(range, selectedMonth);
        AwsBillingSourceResolver.ResolvedBilling resolved = billingSourceResolver == null ? null
            : billingSourceResolver.resolve(session.accountId(), selected.start(), selected.end());
        List<AwsBilling> rows = resolved == null
            ? sourceRows(session.accountId(), selectedMonth, selected.start(), selected.end()) : resolved.rows();
        String dataSource = resolved == null ? "CUR_2_0" : resolved.dataSource();
        Map<String, BigDecimal> grouped = rows.stream().filter(row -> row.getCostAmount() != null)
            .collect(Collectors.groupingBy(row -> row.getServiceName() == null || row.getServiceName().isBlank()
                    ? "Others" : row.getServiceName(),
                Collectors.reducing(BigDecimal.ZERO, AwsBilling::getCostAmount, BigDecimal::add)));
        BigDecimal total = grouped.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<AwsDashboardChartResponse.ServicePoint> services = grouped.entrySet().stream()
            .map(row -> new AwsDashboardChartResponse.ServicePoint(row.getKey(), chartMoney(row.getValue()), percentage(row.getValue(), total)))
            .sorted((left, right) -> right.cost().compareTo(left.cost())).toList();
        String responseCurrency = rows.stream().map(AwsBilling::getCurrency).filter(value -> value != null && !value.isBlank())
            .findFirst().orElseGet(() -> currency(session.accountId()));
        return new AwsDashboardChartResponse(session.accountId(), responseCurrency, selected.name(), List.of(), services,
            grouped.isEmpty() ? null : chartMoney(total), grouped.isEmpty() ? "NO_BILLING_DATA" : "AVAILABLE", dataSource);
    }

    private AwsDashboardResponse databaseDashboard(String accountId, AwsResourceResponse resources, YearMonth selectedMonth) {
        LocalDate monthStart = selectedMonth.atDay(1);
        LocalDate nextMonth = monthStart.plusMonths(1);
        List<AwsBilling> allRows = sourceRows(accountId, selectedMonth, selectedMonth.minusMonths(5).atDay(1), nextMonth);
        List<AwsBilling> current = sourceRows(accountId, selectedMonth, monthStart, nextMonth);
        BigDecimal currentCost = current.stream().map(AwsBilling::getCostAmount).filter(value -> value != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<AwsBilling> previous = sourceRows(accountId, selectedMonth.minusMonths(1), monthStart.minusMonths(1), monthStart);
        BigDecimal previousCost = previous.stream().map(AwsBilling::getCostAmount).filter(value -> value != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal change = previous.isEmpty() || previousCost.signum() == 0 ? null : currentCost.subtract(previousCost).multiply(BigDecimal.valueOf(100)).divide(previousCost, 2, RoundingMode.HALF_UP);
        Map<String, BigDecimal> services = current.stream().collect(Collectors.groupingBy(row -> row.getServiceName() == null ? "Other AWS services" : row.getServiceName(), Collectors.reducing(BigDecimal.ZERO, row -> row.getCostAmount() == null ? BigDecimal.ZERO : row.getCostAmount(), BigDecimal::add)));
        List<ServiceCost> distribution = services.entrySet().stream().sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed()).map(entry -> new ServiceCost(entry.getKey(), money(entry.getValue()))).toList();
        List<CostPoint> trend = rowsInRange(allRows, monthStart.minusMonths(5), nextMonth).stream()
            .collect(Collectors.groupingBy(row -> YearMonth.from(billingDate(row)).toString(), Collectors.reducing(BigDecimal.ZERO, row -> row.getCostAmount() == null ? BigDecimal.ZERO : row.getCostAmount(), BigDecimal::add)))
                .entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> new CostPoint(entry.getKey(), entry.getValue().setScale(2, RoundingMode.HALF_UP))).toList();
        String rowCurrency = current.stream().map(AwsBilling::getCurrency).filter(value -> value != null && !value.isBlank()).findFirst().orElse(CURRENCY);
        AwsDashboardResponse.Spend spend = current.isEmpty() ? null : new AwsDashboardResponse.Spend(currentCost.setScale(2, RoundingMode.HALF_UP), rowCurrency, selectedMonth.toString(), previous.isEmpty() ? null : previousCost.setScale(2, RoundingMode.HALF_UP), change);
        AwsDashboardResponse.Savings savings = recommendationRepository == null ? null : recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(accountId, selectedMonth.toString()).stream()
                .map(item -> item.getPotentialSavings()).filter(value -> value != null && value.signum() > 0).reduce(BigDecimal.ZERO, BigDecimal::add).signum() > 0
            ? new AwsDashboardResponse.Savings(recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(accountId, selectedMonth.toString()).stream().map(item -> item.getPotentialSavings()).filter(value -> value != null && value.signum() > 0).reduce(BigDecimal.ZERO, BigDecimal::add), rowCurrency, selectedMonth.toString()) : null;
        AwsDashboardResponse.BudgetUsage budget = budgetUsage(accountId, selectedMonth, currentCost, rowCurrency);
        String status = current.isEmpty() ? "NO_DATA" : "AVAILABLE";
        String dataSource = billingSourceResolver == null ? "CUR_2_0"
            : billingSourceResolver.resolve(accountId, monthStart, nextMonth).dataSource();
        return new AwsDashboardResponse(accountId, spend, new AwsDashboardResponse.ActiveResources(resources.summary().activeResources()), savings, budget, trend, distribution,
            new AwsDashboardResponse.Billing(status, dataSource, current.isEmpty() ? null : selectedMonth.atEndOfMonth().toString(), monthStart.toString(), nextMonth.minusDays(1).toString(), current.isEmpty() ? "No billing rows for the selected month." : null));
    }

    private List<AwsBilling> rowsInRange(List<AwsBilling> rows, LocalDate start, LocalDate end) {
        return rows.stream().filter(row -> {
            LocalDate date = row.getUsageStartDate() == null ? row.getBillingPeriodStart() : row.getUsageStartDate();
            return date != null && !date.isBefore(start) && date.isBefore(end);
        }).toList();
    }

    private LocalDate billingDate(AwsBilling row) {
        return row.getUsageStartDate() == null ? row.getBillingPeriodStart() : row.getUsageStartDate();
    }

    private YearMonth selectedMonth(String month) {
        return month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month);
    }

    private BudgetUsage budgetUsage(String accountId, YearMonth month, BigDecimal actualSpend, String currency) {
        if (budgetRepository == null) return null;
        Budget budget = budgetRepository.findFirstByAwsAccountIdAndStatusOrderByCreatedAtDesc(accountId, "ACTIVE").orElse(null);
        if (budget == null) return null;
        List<AwsBilling> rows = billingSourceResolver == null
            ? rowsInRange(billingRepository.findByAwsAccountId(accountId), month.atDay(1), month.plusMonths(1).atDay(1))
            : billingSourceResolver.resolve(accountId, month).rows();
        BigDecimal spend = rows.isEmpty() ? null : rows.stream().map(AwsBilling::getCostAmount).filter(value -> value != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal percentage = spend == null || budget.getAmount() == null || budget.getAmount().signum() == 0 ? null : spend.multiply(BigDecimal.valueOf(100)).divide(budget.getAmount(), 2, RoundingMode.HALF_UP);
        String status = percentage == null ? "NO_BILLING_DATA" : percentage.compareTo(BigDecimal.valueOf(100)) >= 0 ? "OVER_BUDGET" : percentage.compareTo(BigDecimal.valueOf(80)) >= 0 ? "NEAR_LIMIT" : "WITHIN_LIMIT";
        return new BudgetUsage(true, percentage, budget.getAmount(), spend, budget.getCurrency(), status);
    }

    private String currency(String accountId) {
        return billingRepository.findCurrencies(accountId).stream().findFirst().orElse(CURRENCY);
    }

    private ChartRange chartRange(String value, YearMonth selectedMonth) {
        LocalDate end = selectedMonth.plusMonths(1).atDay(1);
        String range = value == null ? "30D" : value.toUpperCase(Locale.ROOT);
        return switch (range) {
            case "7D" -> new ChartRange("7D", end.minusDays(7), end, false);
            case "3M" -> new ChartRange("3M", selectedMonth.minusMonths(2).atDay(1), end, true);
            case "6M" -> new ChartRange("6M", selectedMonth.minusMonths(5).atDay(1), end, true);
            case "1Y" -> new ChartRange("1Y", selectedMonth.minusMonths(11).atDay(1), end, true);
            default -> new ChartRange("30D", end.minusDays(30), end, false);
        };
    }

    private List<AwsBilling> sourceRows(String accountId, YearMonth month, LocalDate start, LocalDate end) {
        if (billingSourceResolver != null) return billingSourceResolver.resolve(accountId, start, end).rows();
        return billingRepository.findByAwsAccountId(accountId).stream().filter(row -> !billingDate(row).isBefore(start) && billingDate(row).isBefore(end)).toList();
    }

    private record ChartRange(String name, LocalDate start, LocalDate end, boolean monthly) {}

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal chartMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP) : value.setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal percentage(BigDecimal value, BigDecimal total) {
        if (value == null || total == null || total.signum() == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }

}
