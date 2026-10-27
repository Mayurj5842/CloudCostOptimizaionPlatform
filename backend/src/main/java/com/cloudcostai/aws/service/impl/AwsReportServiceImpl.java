package com.cloudcostai.aws.service.impl;

import com.cloudcostai.aws.config.AwsBillingSyncProperties;
import com.cloudcostai.aws.dto.response.AwsCostAnalyticsResponse.CostBreakdown;
import com.cloudcostai.aws.dto.response.AwsCostAnalyticsResponse.CostDriver;
import com.cloudcostai.aws.dto.response.AwsCostAnalyticsResponse.CostPoint;
import com.cloudcostai.aws.dto.response.AwsInsightResponse.Insight;
import com.cloudcostai.aws.dto.response.AwsRecommendationResponse.Recommendation;
import com.cloudcostai.aws.dto.response.AwsReportResponse;
import com.cloudcostai.aws.dto.response.AwsReportResponse.SavingsSummary;
import com.cloudcostai.aws.dto.response.AwsReportResponse.Summary;
import com.cloudcostai.aws.dto.response.AwsResourceResponse;
import com.cloudcostai.aws.model.AwsBilling;
import com.cloudcostai.aws.repository.AwsBillingRepository;
import com.cloudcostai.aws.service.AwsConnectionSessionService;
import com.cloudcostai.aws.service.AwsInsightService;
import com.cloudcostai.aws.service.AwsRecommendationService;
import com.cloudcostai.aws.service.AwsReportService;
import com.cloudcostai.aws.service.AwsResourceService;
import com.cloudcostai.aws.service.AwsBillingSourceResolver;
import com.cloudcostai.billing.dto.AlertResponse;
import com.cloudcostai.billing.dto.BudgetResponse;
import com.cloudcostai.billing.service.BudgetService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AwsReportServiceImpl implements AwsReportService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private final AwsConnectionSessionService sessionService;
    private final AwsBillingRepository billingRepository;
    private final AwsBillingSyncProperties billingProperties;
    private final AwsInsightService insightService;
    private final AwsRecommendationService recommendationService;
    private final AwsResourceService resourceService;
    private final BudgetService budgetService;
    private final AwsBillingSourceResolver billingSourceResolver;

    @Autowired
    public AwsReportServiceImpl(AwsConnectionSessionService sessionService,
                                AwsBillingRepository billingRepository,
                                AwsBillingSyncProperties billingProperties,
                                AwsInsightService insightService,
                                AwsRecommendationService recommendationService,
                                AwsResourceService resourceService,
                                BudgetService budgetService,
                                AwsBillingSourceResolver billingSourceResolver) {
        this.sessionService = sessionService;
        this.billingRepository = billingRepository;
        this.billingProperties = billingProperties;
        this.insightService = insightService;
        this.recommendationService = recommendationService;
        this.resourceService = resourceService;
        this.budgetService = budgetService;
        this.billingSourceResolver = billingSourceResolver;
    }

    public AwsReportServiceImpl(AwsConnectionSessionService sessionService,
                                AwsBillingRepository billingRepository,
                                AwsBillingSyncProperties billingProperties,
                                AwsInsightService insightService,
                                AwsRecommendationService recommendationService,
                                AwsResourceService resourceService) {
                    this(sessionService, billingRepository, billingProperties, insightService, recommendationService, resourceService, null, null);
    }

    @Override
    public AwsReportResponse getReport(String connectionToken, String month) {
        return getReport(connectionToken, AwsReportService.ReportParameters.forMonth(month));
    }

    @Override
    public AwsReportResponse getReport(String connectionToken, AwsReportService.ReportParameters parameters) {
        var session = sessionService.getSession(connectionToken);
        DateRange selected = resolvePeriod(parameters);
        String billingMonth = selected.start().toString().substring(0, 7);
        AwsBillingSourceResolver.ResolvedBilling currentBilling = billingSourceResolver == null ? null
            : billingSourceResolver.resolve(session.accountId(), selected.start(), selected.end());
        String dataSource = currentBilling == null ? "CUR_2_0" : currentBilling.dataSource();
        List<AwsBilling> currentRows = currentBilling == null ? billingRepository.findByAwsAccountId(session.accountId()) : currentBilling.rows();
        List<AwsBilling> previousRows = billingSourceResolver == null ? billingRepository.findByAwsAccountId(session.accountId())
            : billingSourceResolver.resolve(session.accountId(), selected.start().minusMonths(1), selected.start()).rows();
        List<AwsBilling> current = validRows(currentRows, selected, parameters.service(), parameters.region());
        List<AwsBilling> previous = validRows(previousRows, new DateRange(selected.start().minusMonths(1), selected.start()), null, null);
        String currency = current.stream().map(AwsBilling::getCurrency).filter(this::present).findFirst().orElse("USD");
        BigDecimal total = sum(current);
        Instant generatedAt = Instant.now();
        if (current.isEmpty() || total.signum() <= 0) {
            return new AwsReportResponse(session.accountId(), "NO_DATA", dataSource, billingMonth,
                    selected.start().toString(), selected.end().minusDays(1).toString(), generatedAt, null, currency,
                    new Summary(null, null, null, null), List.of(), List.of(), List.of(), null, "NOT_AVAILABLE",
                    List.of(), List.of(), new SavingsSummary(null, false),
                    "No billing data was found for the selected billing period.", List.of(), List.of(),
                    List.of(), List.of());
        }

        String groupBy = parameters.groupBy() == null ? "Service" : parameters.groupBy();
        Function<AwsBilling, String> groupKey = row -> {
            if ("Region".equalsIgnoreCase(groupBy)) return blank(row.getRegion(), "Unknown");
            if ("Date".equalsIgnoreCase(groupBy)) return effectiveDate(row).toString();
            return blank(row.getServiceName(), "Unknown");
        };
        Map<String, BigDecimal> grouped = totals(current, groupKey);
        List<CostBreakdown> serviceBreakdown = grouped.entrySet().stream()
                .map(item -> new CostBreakdown(item.getKey(), money(item.getValue())))
                .sorted(Comparator.comparing(CostBreakdown::cost).reversed()).toList();
        List<CostPoint> trend = totals(current, row -> effectiveDate(row).toString()).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(item -> new CostPoint(item.getKey(), money(item.getValue()))).toList();
        List<CostDriver> drivers = serviceBreakdown.stream().limit(5)
                .map(item -> new CostDriver(item.name(), item.cost(), percentage(item.cost(), total))).toList();
        BigDecimal previousTotal = sum(previous);
        BigDecimal changeAmount = previousTotal.signum() == 0 ? null : money(total.subtract(previousTotal));
        BigDecimal changePercentage = previousTotal.signum() == 0 ? null
                : total.subtract(previousTotal).multiply(BigDecimal.valueOf(100)).divide(previousTotal, 2, RoundingMode.HALF_UP);

        AwsResourceResponse resources = null;
        String resourceStatus = "UNAVAILABLE";
        boolean partial = false;
        try {
            resources = resourceService.discoverResources(connectionToken);
            resourceStatus = resources == null ? "UNAVAILABLE" : "AVAILABLE";
            partial = resources == null;
        } catch (RuntimeException ignored) {
            partial = true;
        }

        List<Insight> insights = List.of();
        try {
            insights = insightService.getInsights(connectionToken, billingMonth).insights();
        } catch (RuntimeException ignored) {
            partial = true;
        }
        List<Recommendation> recommendations = List.of();
        BigDecimal savings = null;
        try {
            var recommendationResponse = recommendationService.getRecommendations(connectionToken, billingMonth);
            recommendations = recommendationResponse.recommendations();
            savings = recommendations.stream().map(Recommendation::estimatedMonthlySaving)
                    .filter(value -> value != null && value.signum() > 0).reduce(BigDecimal.ZERO, BigDecimal::add);
        } catch (RuntimeException ignored) {
            partial = true;
        }

        List<BudgetResponse> budgets = List.of();
        List<AlertResponse> alerts = List.of();
        try {
            if (budgetService != null) {
                budgets = budgetService.getBudgetsForAccount(session.accountId(), billingMonth);
                alerts = budgetService.getAlertsForAccount(session.accountId(), billingMonth);
            }
        } catch (RuntimeException ignored) {
            partial = true;
        }
        String status = partial ? "PARTIAL" : "SUCCESS";
        List<String> availableServices = current.stream().map(AwsBilling::getServiceName).filter(this::present)
                .distinct().sorted().toList();
        List<String> availableRegions = current.stream().map(AwsBilling::getRegion).filter(this::present)
                .distinct().sorted().toList();
        return new AwsReportResponse(session.accountId(), status, dataSource, billingMonth,
                selected.start().toString(), selected.end().minusDays(1).toString(), generatedAt, latestUpdated(session.accountId(), selected), currency,
                new Summary(money(total), previousTotal.signum() == 0 ? null : money(previousTotal), changeAmount, changePercentage),
                serviceBreakdown, trend, drivers, resources == null ? null : resources.summary(), resourceStatus,
                insights, recommendations, new SavingsSummary(savings == null || savings.signum() == 0 ? null : money(savings), savings != null && savings.signum() > 0),
                status.equals("PARTIAL") ? "Some live report sections were unavailable; stored billing data remains valid." : null,
                budgets, alerts, availableServices, availableRegions);
    }

    private List<AwsBilling> validRows(List<AwsBilling> rows, DateRange range) {
        return validRows(rows, range, null, null);
    }

    private List<AwsBilling> validRows(List<AwsBilling> rows, DateRange range, String service, String region) {
        return rows.stream().filter(row -> {
            LocalDate date = effectiveDate(row);
            if (date == null || date.isBefore(range.start()) || !date.isBefore(range.end())) return false;
            if (row.getCostAmount() == null || row.getCostAmount().signum() <= 0) return false;
            if (service != null && !service.isBlank() && !"All Services".equalsIgnoreCase(service)) {
                String s = row.getServiceName();
                if (s == null || !s.equalsIgnoreCase(service)) return false;
            }
            if (region != null && !region.isBlank() && !"All Regions".equalsIgnoreCase(region) && !"All".equalsIgnoreCase(region)) {
                String r = row.getRegion();
                if (r == null || !r.equalsIgnoreCase(region)) return false;
            }
            return true;
        }).toList();
    }

    private Map<String, BigDecimal> totals(List<AwsBilling> rows, Function<AwsBilling, String> key) {
        return rows.stream().collect(Collectors.groupingBy(row -> blank(key.apply(row), "Unknown"),
                Collectors.reducing(BigDecimal.ZERO, AwsBilling::getCostAmount, BigDecimal::add)));
    }

    private BigDecimal sum(List<AwsBilling> rows) { return rows.stream().map(AwsBilling::getCostAmount).reduce(BigDecimal.ZERO, BigDecimal::add); }
    private LocalDate effectiveDate(AwsBilling row) { return row.getUsageStartDate() != null ? row.getUsageStartDate() : row.getBillingPeriodStart(); }
    private BigDecimal percentage(BigDecimal value, BigDecimal total) { return total.signum() == 0 ? ZERO : value.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP); }
    private BigDecimal money(BigDecimal value) { return value == null ? null : value.setScale(2, RoundingMode.HALF_UP); }
    private boolean present(String value) { return value != null && !value.isBlank(); }
    private String blank(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }

    private Instant latestUpdated(String accountId, DateRange range) {
        List<AwsBilling> rows = billingSourceResolver == null ? billingRepository.findByAwsAccountId(accountId)
                : billingSourceResolver.resolve(accountId, range.start(), range.end()).rows();
        return rows.stream().map(AwsBilling::getUpdatedAt)
                .filter(value -> value != null).max(Comparator.naturalOrder())
                .map(value -> value.toInstant(java.time.ZoneOffset.UTC)).orElse(null);
    }

    private DateRange resolvePeriod(AwsReportService.ReportParameters parameters) {
        if (parameters.startDate() != null && parameters.endDate() != null) {
            return new DateRange(parameters.startDate(), parameters.endDate().plusDays(1));
        }
        String month = parameters.month();
        if (month == null || month.isBlank()) throw new IllegalArgumentException("month or startDate/endDate is required");
        YearMonth parsed = YearMonth.parse(month);
        return new DateRange(parsed.atDay(1), parsed.plusMonths(1).atDay(1));
    }

    private record DateRange(LocalDate start, LocalDate end) {}
}
