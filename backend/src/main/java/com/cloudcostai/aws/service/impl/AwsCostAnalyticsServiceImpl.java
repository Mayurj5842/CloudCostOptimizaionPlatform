package com.cloudcostai.aws.service.impl;

import com.cloudcostai.aws.config.AwsClientFactory;
import com.cloudcostai.aws.config.AwsBillingSyncProperties;
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
import com.cloudcostai.aws.service.AwsConnectionSessionService;
import com.cloudcostai.aws.service.AwsCostAnalyticsService;
import com.cloudcostai.aws.model.AwsBilling;
import com.cloudcostai.aws.repository.AwsBillingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.budgets.BudgetsClient;
import software.amazon.awssdk.services.budgets.model.DescribeBudgetsRequest;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.costexplorer.model.DateInterval;
import software.amazon.awssdk.services.costexplorer.model.Dimension;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageRequest;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageResponse;
import software.amazon.awssdk.services.costexplorer.model.GetCostForecastRequest;
import software.amazon.awssdk.services.costexplorer.model.Granularity;
import software.amazon.awssdk.services.costexplorer.model.Group;
import software.amazon.awssdk.services.costexplorer.model.GroupDefinition;
import software.amazon.awssdk.services.costexplorer.model.GroupDefinitionType;
import software.amazon.awssdk.services.costexplorer.model.Metric;
import software.amazon.awssdk.services.costexplorer.model.ResultByTime;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AwsCostAnalyticsServiceImpl implements AwsCostAnalyticsService {
    private static final Logger logger = LoggerFactory.getLogger(AwsCostAnalyticsServiceImpl.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    // Use SDK v2 metric key
    private static final String COST_METRIC = "UnblendedCost";
    private final AwsClientFactory clientFactory;
    private final AwsConnectionSessionService sessionService;
    private final AwsBillingSyncProperties billingSyncProperties;
    private final AwsBillingRepository billingRepository;

    public AwsCostAnalyticsServiceImpl(AwsClientFactory clientFactory, AwsConnectionSessionService sessionService,
                                       AwsBillingSyncProperties billingSyncProperties, AwsBillingRepository billingRepository) {
        this.clientFactory = clientFactory;
        this.sessionService = sessionService;
        this.billingSyncProperties = billingSyncProperties;
        this.billingRepository = billingRepository;
    }

    @Override
    public AwsCostAnalyticsResponse getAnalytics(String connectionToken, String periodName) {
        AwsConnectionSessionService.ConnectionSession session = sessionService.getSession(connectionToken);
        DateRange selected = dateRange(periodName);
        return databaseAnalytics(session.accountId(), selected);
    }

        /* COST EXPLORER TEMPORARILY DISABLED
         * CLOUDCOSTAI USES CUR 2.0 + S3 FOR BILLING DATA
         * DO NOT CALL AWS COST EXPLORER FROM DASHBOARD/BILLING FLOW
         */
        /*
        DateRange currentMonth = dateRange("CURRENT_MONTH");

        // COST EXPLORER TEMPORARILY DISABLED
        // DO NOT ENABLE UNTIL BILLING ARCHITECTURE IS UPDATED
        // AWS COST EXPLORER API CALL IS DISABLED TO PREVENT ADDITIONAL COST
        if (!billingSyncProperties.isEnabled()) {
            return disabledResponse(session.accountId(), selected);
        }
        
        logger.info("AWS Cost Analytics requested for account={} period={} dateRange={} to {}", 
            session.accountId(), periodName, selected.start(), selected.end());

        try (CostExplorerClient costExplorer = clientFactory.createCostExplorerClient(session.accessKey(), session.secretKey());
             BudgetsClient budgets = clientFactory.createBudgetsClient(session.accessKey(), session.secretKey())) {
            logger.debug("Cost Explorer client created for account={}", session.accountId());
            
            // Request daily costs grouped by service so we can build a stacked per-day per-service series
            GetCostAndUsageResponse dailyByService = usage(costExplorer, selected, Granularity.DAILY, Dimension.SERVICE);
            logger.debug("Daily (by service) results={} results", dailyByService.resultsByTime().size());

            // Build per-day per-service list and aggregate service totals
            Map<String, BigDecimal> serviceTotals = new HashMap<>();
            List<AwsCostAnalyticsResponse.DailyServiceCost> dailyServiceCosts = new ArrayList<>();
            for (ResultByTime result : dailyByService.resultsByTime()) {
                List<CostBreakdown> servicesForDay = new ArrayList<>();
                for (Group group : result.groups()) {
                    String name = group.keys().isEmpty() || group.keys().get(0).isBlank() ? "Others" : group.keys().get(0);
                    var mv = group.metrics().get(COST_METRIC);
                    BigDecimal cost = mv == null || mv.amount() == null ? ZERO : money(mv.amount());
                    servicesForDay.add(new CostBreakdown(name, cost));
                    serviceTotals.merge(name, cost, BigDecimal::add);
                }
                dailyServiceCosts.add(new AwsCostAnalyticsResponse.DailyServiceCost(result.timePeriod().start(), servicesForDay));
            }

            GetCostAndUsageResponse regions = usage(costExplorer, selected, Granularity.MONTHLY, Dimension.REGION);
            logger.debug("Region costs results={} results", regions.resultsByTime().size());

            GetCostAndUsageResponse monthToDate = usage(costExplorer, currentMonth, Granularity.DAILY, null);
            logger.debug("Month-to-date costs results={} results", monthToDate.resultsByTime().size());

            // build daily trend totals by summing dailyServiceCosts
            List<CostPoint> dailyTrend = dailyServiceCosts.stream().map(d -> {
                BigDecimal dayTotal = d.services().stream().map(CostBreakdown::cost).reduce(ZERO, BigDecimal::add);
                return new CostPoint(d.date(), dayTotal);
            }).toList();
            BigDecimal total = dailyTrend.stream().map(CostPoint::cost).reduce(ZERO, BigDecimal::add);
            BigDecimal thisMonth = dailyPoints(monthToDate).stream().map(CostPoint::cost).reduce(ZERO, BigDecimal::add);
            BigDecimal forecast = forecast(costExplorer, currentMonth);
            List<CostBreakdown> serviceCosts = serviceTotals.entrySet().stream().map(item -> new CostBreakdown(item.getKey(), item.getValue().setScale(2, RoundingMode.HALF_UP))).sorted(Comparator.comparing(CostBreakdown::cost).reversed()).toList();
            logger.info("Cost Explorer service breakdown for account={} range={} to {} services={}",
                    session.accountId(), selected.start(), selected.end(), serviceCosts.stream().map(item -> item.name() + "=" + item.cost()).collect(Collectors.joining(", ")));
            List<CostBreakdown> regionCosts = breakdowns(regions);
            List<CostDriver> drivers = serviceCosts.stream()
                    .map(item -> new CostDriver(item.name(), item.cost(), percentage(item.cost(), total)))
                    .sorted(Comparator.comparing(CostDriver::cost).reversed()).limit(5).toList();
            Budget budget = budget(budgets, session.accountId(), thisMonth);

            logger.info("AWS Cost Analytics completed for account={} totalCost={} serviceCount={} regionCount={}",
                session.accountId(), total, serviceCosts.size(), regionCosts.size());
            
                return new AwsCostAnalyticsResponse(session.accountId(), "USD",
                    new Period(selected.start().toString(), selected.end().minusDays(1).toString()),
                    new Summary(total, thisMonth, dailyAverage(thisMonth, dailyPoints(monthToDate).size()), forecast, null),
                    dailyTrend, dailyServiceCosts, serviceCosts, regionCosts, drivers, anomalies(dailyTrend), budget,
                    new PotentialSavings(false, null), "LATEST_AVAILABLE");
        } catch (RuntimeException exception) {
            logger.warn("AWS Cost Explorer data unavailable for account {}: {} - {}", 
                session.accountId(), exception.getClass().getSimpleName(), exception.getMessage());
                    return new AwsCostAnalyticsResponse(session.accountId(), "USD",
                        new Period(selected.start().toString(), selected.end().minusDays(1).toString()),
                        new Summary(ZERO, ZERO, ZERO, null, null),
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                        new Budget(false, null, ZERO, null, "USD"),
                        new PotentialSavings(false, null), "UNAVAILABLE");
        }
    }
        */

    private AwsCostAnalyticsResponse databaseAnalytics(String accountId, DateRange selected) {
        List<AwsBilling> rows = billingRepository.findByAwsAccountIdAndUsageStartDateBetween(accountId, selected.start(), selected.end());
        if (rows.isEmpty()) return new AwsCostAnalyticsResponse(accountId, "USD",
                new Period(selected.start().toString(), selected.end().minusDays(1).toString()),
                new Summary(ZERO, ZERO, ZERO, null, null), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new Budget(false, null, ZERO, null, "USD"), new PotentialSavings(false, null), "NO_CUR_DATA");
        Map<String, BigDecimal> services = rows.stream().collect(Collectors.groupingBy(row -> blank(row.getServiceName(), "Others"), Collectors.reducing(ZERO, AwsBilling::getCostAmount, BigDecimal::add)));
        Map<String, BigDecimal> regions = rows.stream().collect(Collectors.groupingBy(row -> blank(row.getRegion(), "Global / No Region"), Collectors.reducing(ZERO, AwsBilling::getCostAmount, BigDecimal::add)));
        Map<String, BigDecimal> days = rows.stream().collect(Collectors.groupingBy(row -> row.getUsageStartDate().toString(), Collectors.reducing(ZERO, AwsBilling::getCostAmount, BigDecimal::add)));
        List<CostPoint> trend = days.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> new CostPoint(entry.getKey(), money(entry.getValue()))).toList();
        BigDecimal total = rows.stream().map(AwsBilling::getCostAmount).reduce(ZERO, BigDecimal::add);
        LocalDate monthStart = YearMonth.now().atDay(1);
        BigDecimal thisMonth = rows.stream().filter(row -> !row.getUsageStartDate().isBefore(monthStart)).map(AwsBilling::getCostAmount).reduce(ZERO, BigDecimal::add);
        List<CostBreakdown> serviceCosts = breakdown(services);
        List<CostBreakdown> regionCosts = breakdown(regions);
        List<DailyServiceCost> daily = days.keySet().stream().sorted().map(day -> new DailyServiceCost(day, rows.stream().filter(row -> row.getUsageStartDate().toString().equals(day)).collect(Collectors.groupingBy(row -> blank(row.getServiceName(), "Others"), Collectors.reducing(ZERO, AwsBilling::getCostAmount, BigDecimal::add))).entrySet().stream().map(entry -> new CostBreakdown(entry.getKey(), money(entry.getValue()))).toList())).toList();
        List<CostDriver> drivers = serviceCosts.stream().map(item -> new CostDriver(item.name(), item.cost(), percentage(item.cost(), total))).limit(5).toList();
        return new AwsCostAnalyticsResponse(accountId, rows.get(0).getCurrency(), new Period(selected.start().toString(), selected.end().minusDays(1).toString()),
                new Summary(money(total), money(thisMonth), dailyAverage(thisMonth, days.size()), null, null), trend, daily, serviceCosts, regionCosts, drivers, anomalies(trend),
                new Budget(false, null, thisMonth, null, rows.get(0).getCurrency()), new PotentialSavings(false, null), "LATEST_CUR_DATA");
    }

    private String blank(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private List<CostBreakdown> breakdown(Map<String, BigDecimal> values) { return values.entrySet().stream().map(entry -> new CostBreakdown(entry.getKey(), money(entry.getValue()))).sorted(Comparator.comparing(CostBreakdown::cost).reversed()).toList(); }

    private GetCostAndUsageResponse usage(CostExplorerClient client, DateRange range, Granularity granularity, Dimension group) {
        GetCostAndUsageRequest.Builder request = GetCostAndUsageRequest.builder()
                .timePeriod(interval(range)).granularity(granularity).metrics(List.of(COST_METRIC.toString()));
        if (group != null) {
            request.groupBy(GroupDefinition.builder().type(GroupDefinitionType.DIMENSION).key(group.toString()).build());
        }
        return client.getCostAndUsage(request.build());
    }

    private AwsCostAnalyticsResponse disabledResponse(String accountId, DateRange selected) {
        return new AwsCostAnalyticsResponse(accountId, "USD",
                new Period(selected.start().toString(), selected.end().minusDays(1).toString()),
                new Summary(ZERO, ZERO, ZERO, null, null),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new Budget(false, null, ZERO, null, "USD"),
                new PotentialSavings(false, null), "COST_EXPLORER_DISABLED");
    }

    private BigDecimal forecast(CostExplorerClient client, DateRange range) {
        if (!range.start().equals(dateRange("CURRENT_MONTH").start())) return null;
        try {
            var response = client.getCostForecast(GetCostForecastRequest.builder()
                    .timePeriod(interval(range)).granularity(Granularity.MONTHLY).metric(Metric.UNBLENDED_COST).build());
            return response.total() == null ? null : money(response.total().amount());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Budget budget(BudgetsClient client, String accountId, BigDecimal actualSpend) {
        var budgets = client.describeBudgets(DescribeBudgetsRequest.builder().accountId(accountId).build()).budgets();
        if (budgets.isEmpty()) return new Budget(false, null, actualSpend, null, "USD");
        var budget = budgets.get(0);
        BigDecimal limit = money(budget.budgetLimit().amount());
        BigDecimal usage = limit.signum() == 0 ? null : actualSpend.multiply(BigDecimal.valueOf(100)).divide(limit, 2, RoundingMode.HALF_UP);
        return new Budget(true, limit, actualSpend, usage, budget.budgetLimit().unit());
    }

    private List<CostBreakdown> breakdowns(GetCostAndUsageResponse response) {
        Map<String, BigDecimal> totals = new HashMap<>();
        for (ResultByTime result : response.resultsByTime()) {
            for (Group group : result.groups()) {
                String name = group.keys().isEmpty() || group.keys().get(0).isBlank() ? "Global / No Region" : group.keys().get(0);
                        BigDecimal cost = group.metrics().get(COST_METRIC) == null ? ZERO : money(group.metrics().get(COST_METRIC).amount());
                totals.merge(name, cost, BigDecimal::add);
            }
        }
        return totals.entrySet().stream().map(item -> new CostBreakdown(item.getKey(), item.getValue().setScale(2, RoundingMode.HALF_UP)))
                .sorted(Comparator.comparing(CostBreakdown::cost).reversed()).toList();
    }

    private List<CostPoint> dailyPoints(GetCostAndUsageResponse response) {
        return response.resultsByTime().stream().map(result -> new CostPoint(result.timePeriod().start(), amount(result))).toList();
    }

    private List<CostAnomaly> anomalies(List<CostPoint> points) {
        if (points.size() < 8) return List.of();
        BigDecimal average = points.subList(0, points.size() - 1).stream().map(CostPoint::cost).reduce(ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(points.size() - 1), 2, RoundingMode.HALF_UP);
        if (average.signum() == 0) return List.of();
        List<CostAnomaly> result = new ArrayList<>();
        for (CostPoint point : points) {
            BigDecimal increase = point.cost().subtract(average).multiply(BigDecimal.valueOf(100)).divide(average, 2, RoundingMode.HALF_UP);
            if (increase.compareTo(BigDecimal.valueOf(50)) >= 0) result.add(new CostAnomaly(point.date(), point.cost(), increase));
        }
        return result;
    }

    private DateInterval interval(DateRange range) {
        return DateInterval.builder().start(range.start().format(DATE_FORMAT)).end(range.end().format(DATE_FORMAT)).build();
    }

    private DateRange dateRange(String name) {
        LocalDate today = LocalDate.now();
        return switch (name == null ? "CURRENT_MONTH" : name.toUpperCase()) {
            case "PREVIOUS_MONTH" -> {
                YearMonth previous = YearMonth.from(today).minusMonths(1);
                yield new DateRange(previous.atDay(1), previous.plusMonths(1).atDay(1));
            }
            case "LAST_30_DAYS" -> new DateRange(today.minusDays(29), today.plusDays(1));
            default -> new DateRange(today.withDayOfMonth(1), today.plusDays(1));
        };
    }

    private BigDecimal dailyAverage(BigDecimal value, int days) {
        return days == 0 ? ZERO : value.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentage(BigDecimal value, BigDecimal total) {
        return total.signum() == 0 ? ZERO : value.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal amount(ResultByTime result) {
        var metric = result.total().get(COST_METRIC);
        return metric == null ? ZERO : money(metric.amount());
    }

    private BigDecimal money(String value) {
        return value == null ? ZERO : new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private record DateRange(LocalDate start, LocalDate end) {}
}