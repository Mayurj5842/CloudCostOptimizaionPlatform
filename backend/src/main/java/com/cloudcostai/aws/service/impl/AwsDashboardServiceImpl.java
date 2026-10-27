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
import com.cloudcostai.aws.service.AwsInsightService;
import com.cloudcostai.aws.service.AwsResourceService;
import com.cloudcostai.aws.service.AwsBillingSourceResolver;
import com.cloudcostai.aws.model.AwsBilling;
import com.cloudcostai.aws.model.AwsRecommendation;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
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
    private final AwsInsightService insightService;

    public AwsDashboardServiceImpl(AwsClientFactory clientFactory,
                                   AwsConnectionSessionService sessionService,
                                   AwsResourceService resourceService,
                                   com.cloudcostai.aws.config.AwsBillingSyncProperties ignored,
                                   AwsBillingRepository billingRepository) {
        this(sessionService, resourceService, billingRepository, null, null, null, null);
    }

    @Autowired
    public AwsDashboardServiceImpl(AwsConnectionSessionService sessionService,
                                   AwsResourceService resourceService,
                                   AwsBillingRepository billingRepository,
                                   AwsRecommendationRepository recommendationRepository,
                                   BudgetRepository budgetRepository,
                                   AwsBillingSourceResolver billingSourceResolver,
                                   AwsInsightService insightService) {
        this.sessionService = sessionService;
        this.resourceService = resourceService;
        this.billingRepository = billingRepository;
        this.recommendationRepository = recommendationRepository;
        this.budgetRepository = budgetRepository;
        this.billingSourceResolver = billingSourceResolver;
        this.insightService = insightService;
    }

    public AwsDashboardServiceImpl(AwsConnectionSessionService sessionService,
                                   AwsResourceService resourceService,
                                   AwsBillingRepository billingRepository,
                                   AwsRecommendationRepository recommendationRepository,
                                   BudgetRepository budgetRepository,
                                   AwsBillingSourceResolver billingSourceResolver) {
        this(sessionService, resourceService, billingRepository, recommendationRepository, budgetRepository, billingSourceResolver, null);
    }

    public AwsDashboardServiceImpl(AwsConnectionSessionService sessionService,
                                   AwsResourceService resourceService,
                                   AwsBillingRepository billingRepository,
                                   AwsRecommendationRepository recommendationRepository,
                                   BudgetRepository budgetRepository) {
        this(sessionService, resourceService, billingRepository, recommendationRepository, budgetRepository, null, null);
    }

    public AwsDashboardResponse getDashboard(String connectionToken) {
        return getDashboard(connectionToken, null);
    }

    @Override
    public AwsDashboardResponse getDashboard(String connectionToken, String month) {
        AwsConnectionSessionService.ConnectionSession session = sessionService.getSession(connectionToken);
        AwsResourceResponse resources = resourceService.discoverResources(connectionToken);
        return databaseDashboard(session.accountId(), resources, selectedMonth(month), connectionToken);
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

    private AwsDashboardResponse databaseDashboard(String accountId, AwsResourceResponse resources, YearMonth selectedMonth, String connectionToken) {
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
        AwsDashboardResponse.Savings savings = savings(accountId, selectedMonth, rowCurrency, connectionToken);
        AwsDashboardResponse.BudgetUsage budget = budgetUsage(accountId, selectedMonth, currentCost, rowCurrency);
        String status = current.isEmpty() ? "NO_DATA" : "AVAILABLE";
        String dataSource = billingSourceResolver == null ? "CUR_2_0"
            : billingSourceResolver.resolve(accountId, monthStart, nextMonth).dataSource();
        AwsDashboardResponse.Forecast forecast = forecast(current, previous, previous.isEmpty() ? null : previousCost, selectedMonth, rowCurrency);
        return new AwsDashboardResponse(accountId, spend, new AwsDashboardResponse.ActiveResources(resources.summary().activeResources()), savings, budget, forecast, trend, distribution,
            new AwsDashboardResponse.Billing(status, dataSource, current.isEmpty() ? null : selectedMonth.atEndOfMonth().toString(), monthStart.toString(), nextMonth.minusDays(1).toString(), current.isEmpty() ? "No billing rows for the selected month." : null));
    }

    /**
     * Projects the selected month from the billing rows that are already stored for it.
     * Rows are expanded into daily buckets so a row covering a whole month contributes its cost evenly per day.
     */
    private AwsDashboardResponse.Forecast forecast(List<AwsBilling> rows, List<AwsBilling> previousRows,
                                                    BigDecimal previousCost, YearMonth month, String currency) {
        int daysInMonth = month.lengthOfMonth();
        YearMonth currentMonth = YearMonth.from(LocalDate.now());
        boolean complete = month.isBefore(currentMonth);
        boolean current = month.equals(currentMonth);
        Map<LocalDate, BigDecimal> daily = dailyCosts(rows, month, currentMonth);
        if (daily.isEmpty()) {
            if (current) {
                BigDecimal priorRate = priorMonthRunRate(previousRows, month.minusMonths(1));
                if (priorRate != null) {
                    BigDecimal projected = priorRate.multiply(BigDecimal.valueOf(daysInMonth));
                    return new AwsDashboardResponse.Forecast(forecastMoney(projected), currency, month.toString(),
                        BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP), priorRate, forecastMoney(projected),
                        previousCost == null ? null : forecastMoney(previousCost),
                        previousCost == null || previousCost.signum() == 0 ? null
                            : forecastMoney(projected.subtract(previousCost).multiply(BigDecimal.valueOf(100)).divide(previousCost, 2, RoundingMode.HALF_UP)),
                        0, daysInMonth, daysInMonth, "PRIOR_MONTH_RUN_RATE");
                }
            }
            return new AwsDashboardResponse.Forecast(null, currency, month.toString(), null, null, null,
                previousCost == null ? null : forecastMoney(previousCost), null, 0, daysInMonth, 0, "NO_BILLING_DATA");
        }
        BigDecimal actualToDate = daily.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        int daysObserved = daily.size();
        BigDecimal dailyRunRate = actualToDate.divide(BigDecimal.valueOf(complete ? daysInMonth : daysObserved), 6, RoundingMode.HALF_UP);
        int remainingDays = complete ? 0 : Math.max(0, daysInMonth - daysObserved);
        BigDecimal projectedRemaining = complete ? BigDecimal.ZERO
            : dailyRunRate.multiply(BigDecimal.valueOf(remainingDays));
        BigDecimal forecastTotal = actualToDate.add(projectedRemaining);
        BigDecimal change = previousCost == null || previousCost.signum() == 0 ? null
            : forecastTotal.subtract(previousCost).multiply(BigDecimal.valueOf(100)).divide(previousCost, 2, RoundingMode.HALF_UP);
        return new AwsDashboardResponse.Forecast(forecastMoney(forecastTotal), currency, month.toString(), forecastMoney(actualToDate),
            dailyRunRate, forecastMoney(projectedRemaining), previousCost == null ? null : forecastMoney(previousCost), change,
            daysObserved, daysInMonth, remainingDays, complete ? "FINAL_ACTUAL" : "RUN_RATE_PROJECTION");
    }

    /** Daily rate of the month before the selected one, used when the selected month has no billing rows yet. */
    private BigDecimal priorMonthRunRate(List<AwsBilling> previousRows, YearMonth previousMonth) {
        if (previousRows == null || previousRows.isEmpty()) return null;
        BigDecimal total = previousRows.stream().map(AwsBilling::getCostAmount).filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() == 0) return null;
        return total.divide(BigDecimal.valueOf(previousMonth.lengthOfMonth()), 6, RoundingMode.HALF_UP);
    }

    /**
     * Spreads every billing row over the days it covers so daily buckets stay comparable across data sources.
     * For a month that is still running, only the days up to today are kept, which keeps month-to-date cost honest.
     */
    private Map<LocalDate, BigDecimal> dailyCosts(List<AwsBilling> rows, YearMonth month, YearMonth currentMonth) {
        Map<LocalDate, BigDecimal> daily = new TreeMap<>();
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();
        LocalDate today = LocalDate.now();
        for (AwsBilling row : rows) {
            BigDecimal cost = row.getCostAmount();
            LocalDate start = billingDate(row);
            if (cost == null || start == null) continue;
            LocalDate end = row.getUsageEndDate() == null ? row.getBillingPeriodEnd() : row.getUsageEndDate();
            if (end == null || end.isBefore(start)) end = start;
            if (start.isBefore(monthStart)) start = monthStart;
            if (end.isAfter(monthEnd)) end = monthEnd;
            if (end.isBefore(start)) continue;
            long covered = ChronoUnit.DAYS.between(start, end) + 1;
            BigDecimal perDay = cost.divide(BigDecimal.valueOf(covered), 10, RoundingMode.HALF_UP);
            for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
                if (month.equals(currentMonth) && day.isAfter(today)) continue;
                daily.merge(day, perDay, BigDecimal::add);
            }
        }
        return daily;
    }

    private AwsDashboardResponse.Savings savings(String accountId, YearMonth month, String currency, String connectionToken) {
        BigDecimal estimate = insightEstimate(connectionToken, month);
        if (estimate == null) estimate = storedRecommendationSavings(accountId, month);
        return estimate == null ? null : new AwsDashboardResponse.Savings(estimate.setScale(2, RoundingMode.HALF_UP), currency, month.toString());
    }

    private BigDecimal insightEstimate(String connectionToken, YearMonth month) {
        if (insightService == null || connectionToken == null) return null;
        try {
            return insightService.estimatedSavings(connectionToken, month.toString());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private BigDecimal storedRecommendationSavings(String accountId, YearMonth month) {
        if (recommendationRepository == null) return null;
        BigDecimal total = recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(accountId, month.toString()).stream()
            .map(AwsRecommendation::getPotentialSavings).filter(value -> value != null && value.signum() > 0).reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.signum() > 0 ? total : null;
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
        Budget budget = configuredBudget(accountId, month);
        if (budget == null) return null;
        List<AwsBilling> rows = billingSourceResolver == null
            ? rowsInRange(billingRepository.findByAwsAccountId(accountId), month.atDay(1), month.plusMonths(1).atDay(1))
            : billingSourceResolver.resolve(accountId, month).rows();
        BigDecimal spend = rows.isEmpty() ? null : rows.stream().map(AwsBilling::getCostAmount).filter(value -> value != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal percentage = spend == null || budget.getAmount() == null || budget.getAmount().signum() == 0 ? null : spend.multiply(BigDecimal.valueOf(100)).divide(budget.getAmount(), 2, RoundingMode.HALF_UP);
        String status = percentage == null ? "NO_BILLING_DATA" : percentage.compareTo(BigDecimal.valueOf(100)) >= 0 ? "OVER_BUDGET" : percentage.compareTo(BigDecimal.valueOf(80)) >= 0 ? "NEAR_LIMIT" : "WITHIN_LIMIT";
        return new BudgetUsage(true, percentage, budget.getAmount(), spend, budget.getCurrency(), status);
    }

    private Budget configuredBudget(String accountId, YearMonth month) {
        if (budgetRepository == null) return null;
        LocalDate monthEnd = month.atEndOfMonth();
        LocalDate monthStart = month.atDay(1);
        return budgetRepository.findFirstByAwsAccountIdAndServiceNameIsNullAndEnabledTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByCreatedAtDesc(accountId, monthEnd, monthStart)
            .or(() -> budgetRepository.findFirstByAwsAccountIdAndEnabledTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByCreatedAtDesc(accountId, monthEnd, monthStart))
            .orElse(null);
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

    /** Forecast amounts keep four decimals so small real spends are not rounded away to zero. */
    private BigDecimal forecastMoney(BigDecimal value) {
        return value == null ? null : value.setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal percentage(BigDecimal value, BigDecimal total) {
        if (value == null || total == null || total.signum() == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }

}
