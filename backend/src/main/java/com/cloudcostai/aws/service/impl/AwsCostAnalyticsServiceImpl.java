package com.cloudcostai.aws.service.impl;

import com.cloudcostai.aws.dto.response.AwsCostAnalyticsResponse;
import com.cloudcostai.aws.dto.response.AwsCostAnalyticsResponse.Budget;
import com.cloudcostai.aws.dto.response.AwsCostAnalyticsResponse.CostAnomaly;
import com.cloudcostai.aws.dto.response.AwsCostAnalyticsResponse.CostBreakdown;
import com.cloudcostai.aws.dto.response.AwsCostAnalyticsResponse.CostDriver;
import com.cloudcostai.aws.dto.response.AwsCostAnalyticsResponse.CostPoint;
import com.cloudcostai.aws.dto.response.AwsCostAnalyticsResponse.DailyServiceCost;
import com.cloudcostai.aws.dto.response.AwsCostAnalyticsResponse.Period;
import com.cloudcostai.aws.dto.response.AwsCostAnalyticsResponse.PotentialSavings;
import com.cloudcostai.aws.dto.response.AwsCostAnalyticsResponse.Summary;
import com.cloudcostai.aws.model.AwsBilling;
import com.cloudcostai.aws.model.AwsRecommendation;
import com.cloudcostai.aws.repository.AwsBillingRepository;
import com.cloudcostai.aws.repository.AwsRecommendationRepository;
import com.cloudcostai.aws.service.AwsConnectionSessionService;
import com.cloudcostai.aws.service.AwsCostAnalyticsService;
import com.cloudcostai.billing.repository.BudgetRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AwsCostAnalyticsServiceImpl implements AwsCostAnalyticsService {
    private static final String DEFAULT_CURRENCY = "USD";
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final AwsConnectionSessionService sessionService;
    private final AwsBillingRepository billingRepository;
    private final AwsRecommendationRepository recommendationRepository;
    private final BudgetRepository budgetRepository;

    public AwsCostAnalyticsServiceImpl(AwsConnectionSessionService sessionService,
                                       AwsBillingRepository billingRepository,
                                       AwsRecommendationRepository recommendationRepository,
                                       BudgetRepository budgetRepository) {
        this.sessionService = sessionService;
        this.billingRepository = billingRepository;
        this.recommendationRepository = recommendationRepository;
        this.budgetRepository = budgetRepository;
    }

    @Override
    public AwsCostAnalyticsResponse getAnalytics(String connectionToken, String period) {
        return getAnalytics(connectionToken, period, null);
    }

    @Override
    public AwsCostAnalyticsResponse getAnalytics(String connectionToken, String period, String month) {
        var session = sessionService.getSession(connectionToken);
        DateRange selected = dateRange(period, month);
        String currency = billingRepository.findCurrencies(session.accountId()).stream().findFirst().orElse(DEFAULT_CURRENCY);
        List<AwsBilling> rows = billingRepository.findByAwsAccountId(session.accountId()).stream()
                .filter(row -> inRange(row, selected.start(), selected.end()))
                .toList();
        List<AwsBilling> previousRows = billingRepository.findByAwsAccountId(session.accountId()).stream()
                .filter(row -> inRange(row, selected.start().minusMonths(1), selected.start()))
                .toList();
        if (rows.isEmpty()) return noData(session.accountId(), selected, currency, previousRows.isEmpty() ? null : sum(previousRows));
        return available(session.accountId(), selected, currency, rows, previousRows);
    }

    private AwsCostAnalyticsResponse available(String accountId, DateRange selected, String currency,
                                               List<AwsBilling> rows, List<AwsBilling> previousRows) {
        BigDecimal total = sum(rows);
        BigDecimal previous = previousRows.isEmpty() ? null : sum(previousRows);
        BigDecimal difference = previous == null ? null : total.subtract(previous).setScale(2, RoundingMode.HALF_UP);
        BigDecimal change = previous == null || previous.signum() == 0 ? null : difference.multiply(BigDecimal.valueOf(100)).divide(previous, 2, RoundingMode.HALF_UP);
        Map<String, BigDecimal> services = totals(rows, row -> blank(row.getServiceName(), "Other AWS services"));
        Map<String, BigDecimal> regions = totals(rows, row -> blank(row.getRegion(), "Global / No Region"));
        Map<String, BigDecimal> days = totals(rows, row -> billingDate(row).toString());
        List<CostPoint> trend = breakdown(days).stream().map(item -> new CostPoint(item.name(), item.cost())).toList();
        List<DailyServiceCost> daily = days.keySet().stream().sorted().map(day -> new DailyServiceCost(day,
                breakdown(totals(rows.stream().filter(row -> day.equals(billingDate(row).toString())).toList(), row -> blank(row.getServiceName(), "Other AWS services"))))).toList();
        List<CostBreakdown> serviceCosts = breakdown(services);
        List<CostBreakdown> regionCosts = breakdown(regions);
        List<CostDriver> drivers = serviceCosts.stream().limit(5).map(item -> new CostDriver(item.name(), item.cost(), percentage(item.cost(), total))).toList();
        String lastUpdated = rows.stream().map(AwsBilling::getUpdatedAt).filter(value -> value != null).max(Comparator.naturalOrder()).map(Object::toString).orElse(null);
        return new AwsCostAnalyticsResponse(accountId, currency, displayPeriod(selected),
                new Summary(money(total), money(total), dailyAverage(total, days.size()), null, null, previous, difference, change), trend, daily, serviceCosts, regionCosts,
                drivers, anomalies(trend), budget(accountId, selected, rows, currency), savings(accountId, currency), "AVAILABLE",
                "AWS_CUR_2_0_POSTGRESQL", selected.start().toString(), selected.end().toString(), lastUpdated, null);
    }

    private AwsCostAnalyticsResponse noData(String accountId, DateRange selected, String currency, BigDecimal previous) {
        return new AwsCostAnalyticsResponse(accountId, currency, displayPeriod(selected),
                new Summary(null, null, null, null, null, previous, null, null), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                budget(accountId, selected, List.of(), currency), savings(accountId, currency), "NO_CUR_DATA",
                "AWS_CUR_2_0_POSTGRESQL", selected.start().toString(), selected.end().toString(), null,
                previous == null ? "No CUR data available for the selected billing period." : "No CUR data available for the selected billing period; previous-period data exists.");
    }

    private Budget budget(String accountId, DateRange selected, List<AwsBilling> rows, String currency) {
        com.cloudcostai.billing.model.Budget configured = budgetRepository.findFirstByAwsAccountIdAndStatusOrderByCreatedAtDesc(accountId, "ACTIVE").orElse(null);
        if (configured == null) return new Budget(false, null, null, null, currency);
        BigDecimal actual = rows.isEmpty() ? null : sum(rows);
        BigDecimal usage = actual == null || configured.getAmount() == null || configured.getAmount().signum() == 0 ? null : actual.multiply(BigDecimal.valueOf(100)).divide(configured.getAmount(), 2, RoundingMode.HALF_UP);
        return new Budget(true, configured.getAmount(), actual, usage, configured.getCurrency());
    }

    private PotentialSavings savings(String accountId, String currency) {
        BigDecimal total = recommendationRepository.findByAwsAccountIdOrderByCreatedAtDesc(accountId).stream()
                .map(AwsRecommendation::getPotentialSavings).filter(value -> value != null && value.signum() > 0).reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.signum() == 0 ? new PotentialSavings(false, null) : new PotentialSavings(true, money(total));
    }

    private Map<String, BigDecimal> totals(List<AwsBilling> rows, java.util.function.Function<AwsBilling, String> key) {
        return rows.stream().filter(row -> row.getCostAmount() != null).collect(Collectors.groupingBy(key, Collectors.reducing(BigDecimal.ZERO, AwsBilling::getCostAmount, BigDecimal::add)));
    }

    private List<CostBreakdown> breakdown(Map<String, BigDecimal> values) {
        return values.entrySet().stream().map(entry -> new CostBreakdown(entry.getKey(), money(entry.getValue())))
                .sorted(Comparator.comparing(CostBreakdown::cost).reversed()).toList();
    }

    private List<CostAnomaly> anomalies(List<CostPoint> points) {
        if (points.size() < 8) return List.of();
        BigDecimal average = points.subList(0, points.size() - 1).stream().map(CostPoint::cost).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(points.size() - 1), 2, RoundingMode.HALF_UP);
        if (average.signum() == 0) return List.of();
        return points.stream().map(point -> point.cost().subtract(average).multiply(BigDecimal.valueOf(100)).divide(average, 2, RoundingMode.HALF_UP))
                .filter(change -> change.compareTo(BigDecimal.valueOf(50)) >= 0)
                .map(change -> new CostAnomaly(points.get(0).date(), points.get(0).cost(), change)).toList();
    }

    private DateRange dateRange(String period, String month) {
        if (month != null && !month.isBlank()) {
            try {
                YearMonth selected = YearMonth.parse(month);
                return new DateRange(selected.atDay(1), selected.plusMonths(1).atDay(1));
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Invalid month format. Please use YYYY-MM.");
            }
        }
        LocalDate today = LocalDate.now();
        return switch (period == null ? "CURRENT_MONTH" : period.toUpperCase()) {
            case "PREVIOUS_MONTH" -> {
                YearMonth previous = YearMonth.from(today).minusMonths(1);
                yield new DateRange(previous.atDay(1), previous.plusMonths(1).atDay(1));
            }
            case "LAST_30_DAYS" -> new DateRange(today.minusDays(29), today.plusDays(1));
            case "CURRENT_MONTH" -> new DateRange(YearMonth.from(today).atDay(1), YearMonth.from(today).plusMonths(1).atDay(1));
            default -> throw new IllegalArgumentException("Invalid billing period.");
        };
    }

    private boolean inRange(AwsBilling row, LocalDate start, LocalDate end) {
        LocalDate date = billingDate(row);
        return date != null && !date.isBefore(start) && date.isBefore(end);
    }

    private LocalDate billingDate(AwsBilling row) { return row.getUsageStartDate() == null ? row.getBillingPeriodStart() : row.getUsageStartDate(); }
    private BigDecimal sum(List<AwsBilling> rows) { return rows.stream().map(AwsBilling::getCostAmount).filter(value -> value != null).reduce(BigDecimal.ZERO, BigDecimal::add); }
    private String blank(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private BigDecimal money(BigDecimal value) { return value == null ? null : value.setScale(2, RoundingMode.HALF_UP); }
    private BigDecimal percentage(BigDecimal value, BigDecimal total) { return total.signum() == 0 ? ZERO : value.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP); }
    private BigDecimal dailyAverage(BigDecimal total, int days) { return days == 0 ? null : total.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP); }
    private Period displayPeriod(DateRange range) { return new Period(range.start().toString(), range.end().minusDays(1).toString()); }
    private record DateRange(LocalDate start, LocalDate end) {}
}
