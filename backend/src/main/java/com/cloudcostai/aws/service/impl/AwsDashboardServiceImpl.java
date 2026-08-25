package com.cloudcostai.aws.service.impl;

import com.cloudcostai.aws.config.AwsClientFactory;
import com.cloudcostai.aws.config.AwsBillingSyncProperties;
import com.cloudcostai.aws.dto.response.AwsDashboardResponse;
import com.cloudcostai.aws.dto.response.AwsDashboardResponse.BudgetUsage;
import com.cloudcostai.aws.dto.response.AwsDashboardResponse.CostPoint;
import com.cloudcostai.aws.dto.response.AwsDashboardResponse.ServiceCost;
import com.cloudcostai.aws.dto.response.AwsDashboardChartResponse;
import com.cloudcostai.aws.dto.response.AwsResourceResponse;
import com.cloudcostai.aws.exception.AwsConnectionException;
import com.cloudcostai.aws.service.AwsConnectionSessionService;
import com.cloudcostai.aws.service.AwsDashboardService;
import com.cloudcostai.aws.service.AwsResourceService;
import com.cloudcostai.aws.model.AwsBilling;
import com.cloudcostai.aws.repository.AwsBillingRepository;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.budgets.BudgetsClient;
import software.amazon.awssdk.services.budgets.model.Budget;
import software.amazon.awssdk.services.budgets.model.DescribeBudgetsRequest;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.costexplorer.model.DateInterval;
import software.amazon.awssdk.services.costexplorer.model.Dimension;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageRequest;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageResponse;
import software.amazon.awssdk.services.costexplorer.model.GroupDefinition;
import software.amazon.awssdk.services.costexplorer.model.GroupDefinitionType;
import software.amazon.awssdk.services.costexplorer.model.Metric;
import software.amazon.awssdk.services.costexplorer.model.ResultByTime;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
// import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AwsDashboardServiceImpl implements AwsDashboardService {
    private static final Logger logger = LoggerFactory.getLogger(AwsDashboardServiceImpl.class);
    private static final String CURRENCY = "USD";
    private static final String COST_METRIC = Metric.UNBLENDED_COST.toString();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private final AwsClientFactory clientFactory;
    private final AwsConnectionSessionService sessionService;
    private final AwsResourceService resourceService;
    private final AwsBillingSyncProperties billingSyncProperties;
    private final AwsBillingRepository billingRepository;

    public AwsDashboardServiceImpl(AwsClientFactory clientFactory,
                                   AwsConnectionSessionService sessionService,
                                   AwsResourceService resourceService,
                                   AwsBillingSyncProperties billingSyncProperties,
                                   AwsBillingRepository billingRepository) {
        this.clientFactory = clientFactory;
        this.sessionService = sessionService;
        this.resourceService = resourceService;
        this.billingSyncProperties = billingSyncProperties;
        this.billingRepository = billingRepository;
    }

    @Override
    public AwsDashboardResponse getDashboard(String connectionToken) {
        AwsConnectionSessionService.ConnectionSession session = sessionService.getSession(connectionToken);
        AwsResourceResponse resources = resourceService.discoverResources(connectionToken);
        return databaseDashboard(session.accountId(), resources);
    }

    @Override
    public AwsDashboardChartResponse getCostTrend(String connectionToken, String range) {
        var session = sessionService.getSession(connectionToken);
        ChartRange selected = chartRange(range);
        List<AwsBillingRepository.CostTrendProjection> rows = selected.monthly()
                ? billingRepository.findMonthlyCostTrend(session.accountId(), selected.start(), selected.end())
                : billingRepository.findDailyCostTrend(session.accountId(), selected.start(), selected.end());
        return new AwsDashboardChartResponse(session.accountId(), currency(session.accountId()), selected.name(),
                rows.stream().map(row -> new AwsDashboardChartResponse.CostPoint(row.getPeriod().toString(), money(row.getCost()))).toList(),
                List.of(), null);
    }

    @Override
    public AwsDashboardChartResponse getServiceDistribution(String connectionToken, String range) {
        var session = sessionService.getSession(connectionToken);
        ChartRange selected = chartRange(range);
        List<AwsBillingRepository.ServiceDistributionProjection> rows = billingRepository.findServiceDistribution(session.accountId(), selected.start(), selected.end());
        BigDecimal total = rows.stream().map(AwsBillingRepository.ServiceDistributionProjection::getCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<AwsDashboardChartResponse.ServicePoint> services = rows.stream()
                .map(row -> new AwsDashboardChartResponse.ServicePoint(row.getService(), money(row.getCost()), percentage(row.getCost(), total))).toList();
        return new AwsDashboardChartResponse(session.accountId(), currency(session.accountId()), selected.name(), List.of(), services, money(total));
    }

        /* COST EXPLORER TEMPORARILY DISABLED
         * CLOUDCOSTAI USES CUR 2.0 + S3 FOR BILLING DATA
         * DO NOT CALL AWS COST EXPLORER FROM DASHBOARD/BILLING FLOW
         */
        /*
        AwsDashboardResponse.Spend spend = null;
        BudgetUsage budget = null;
        List<CostPoint> trend = List.of();
        List<ServiceCost> serviceDistribution = List.of();

        // COST EXPLORER TEMPORARILY DISABLED
        // DO NOT ENABLE UNTIL BILLING ARCHITECTURE IS UPDATED
        // AWS COST EXPLORER API CALL IS DISABLED TO PREVENT ADDITIONAL COST
        if (!billingSyncProperties.isEnabled()) {
            return new AwsDashboardResponse(session.accountId(), null,
                new AwsDashboardResponse.ActiveResources(resources.summary().activeResources()),
                null, null, List.of(), List.of());
        }

        try (CostExplorerClient costExplorer = clientFactory.createCostExplorerClient(session.accessKey(), session.secretKey());
             BudgetsClient budgets = clientFactory.createBudgetsClient(session.accessKey(), session.secretKey())) {
            LocalDate currentMonth = YearMonth.now().atDay(1);
            LocalDate previousMonth = currentMonth.minusMonths(1);
            LocalDate nextMonth = currentMonth.plusMonths(1);
            GetCostAndUsageResponse twoMonths = getCosts(costExplorer, previousMonth, nextMonth, false);
            BigDecimal previousCost = amount(twoMonths, 0);
            BigDecimal currentCost = amount(twoMonths, 1);
            BigDecimal percentageChange = previousCost.signum() == 0 ? null : currentCost.subtract(previousCost)
                .multiply(BigDecimal.valueOf(100)).divide(previousCost, 2, RoundingMode.HALF_UP);

            LocalDate sixMonthsAgo = currentMonth.minusMonths(5);
            GetCostAndUsageResponse trendResponse = getCosts(costExplorer, sixMonthsAgo, nextMonth, false);
            trend = trendResponse.resultsByTime().stream()
                .map(result -> new CostPoint(monthLabel(result), amount(result))).toList();
            GetCostAndUsageResponse serviceResponse = getCosts(costExplorer, currentMonth, nextMonth, true);
            serviceDistribution = serviceCosts(serviceResponse);
            spend = new AwsDashboardResponse.Spend(currentCost, CURRENCY, "CURRENT_MONTH", previousCost, percentageChange);
            budget = budgetUsage(budgets, session.accountId(), currentCost);
        } catch (RuntimeException exception) {
            logger.warn("AWS billing data unavailable for account {}: {}", session.accountId(), exception.getClass().getSimpleName());
        }

        return new AwsDashboardResponse(session.accountId(), spend,
            new AwsDashboardResponse.ActiveResources(resources.summary().activeResources()),
            null, budget, trend, serviceDistribution);
    }
        */

    private AwsDashboardResponse databaseDashboard(String accountId, AwsResourceResponse resources) {
        LocalDate monthStart = YearMonth.now().atDay(1);
        LocalDate nextMonth = monthStart.plusMonths(1);
        List<AwsBilling> current = billingRepository.findByAwsAccountIdAndUsageStartDateBetween(accountId, monthStart, nextMonth);
        BigDecimal currentCost = current.stream().map(AwsBilling::getCostAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<AwsBilling> previous = billingRepository.findByAwsAccountIdAndUsageStartDateBetween(accountId, monthStart.minusMonths(1), monthStart);
        BigDecimal previousCost = previous.stream().map(AwsBilling::getCostAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal change = previousCost.signum() == 0 ? null : currentCost.subtract(previousCost).multiply(BigDecimal.valueOf(100)).divide(previousCost, 2, RoundingMode.HALF_UP);
        Map<String, BigDecimal> services = current.stream().collect(Collectors.groupingBy(row -> row.getServiceName() == null ? "Others" : row.getServiceName(), Collectors.reducing(BigDecimal.ZERO, AwsBilling::getCostAmount, BigDecimal::add)));
        List<ServiceCost> distribution = List.of("EC2", "S3", "RDS", "Lambda").stream().map(name -> new ServiceCost(name, serviceValue(services, name))).toList();
        List<CostPoint> trend = billingRepository.findByAwsAccountIdAndUsageStartDateBetween(accountId, monthStart.minusMonths(5), nextMonth).stream()
                .collect(Collectors.groupingBy(row -> YearMonth.from(row.getUsageStartDate()).toString(), Collectors.reducing(BigDecimal.ZERO, AwsBilling::getCostAmount, BigDecimal::add)))
                .entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> new CostPoint(entry.getKey(), entry.getValue().setScale(2, RoundingMode.HALF_UP))).toList();
        AwsDashboardResponse.Spend spend = current.isEmpty() ? null : new AwsDashboardResponse.Spend(currentCost.setScale(2, RoundingMode.HALF_UP), current.get(0).getCurrency(), "CURRENT_MONTH", previousCost.setScale(2, RoundingMode.HALF_UP), change);
        return new AwsDashboardResponse(accountId, spend, new AwsDashboardResponse.ActiveResources(resources.summary().activeResources()), null, null, trend, distribution);
    }

    private String currency(String accountId) {
        return billingRepository.findCurrencies(accountId).stream().findFirst().orElse(CURRENCY);
    }

    private ChartRange chartRange(String value) {
        LocalDate today = LocalDate.now();
        String range = value == null ? "30D" : value.toUpperCase(Locale.ROOT);
        return switch (range) {
            case "7D" -> new ChartRange("7D", today.minusDays(6), today.plusDays(1), false);
            case "3M" -> new ChartRange("3M", YearMonth.from(today).minusMonths(2).atDay(1), YearMonth.from(today).plusMonths(1).atDay(1), true);
            case "6M" -> new ChartRange("6M", YearMonth.from(today).minusMonths(5).atDay(1), YearMonth.from(today).plusMonths(1).atDay(1), true);
            case "1Y" -> new ChartRange("1Y", YearMonth.from(today).minusMonths(11).atDay(1), YearMonth.from(today).plusMonths(1).atDay(1), true);
            default -> new ChartRange("30D", today.minusDays(29), today.plusDays(1), false);
        };
    }

    private record ChartRange(String name, LocalDate start, LocalDate end, boolean monthly) {}

    private GetCostAndUsageResponse getCosts(CostExplorerClient client, LocalDate start, LocalDate end, boolean grouped) {
        GetCostAndUsageRequest.Builder request = GetCostAndUsageRequest.builder()
                .timePeriod(DateInterval.builder().start(start.format(DATE_FORMAT)).end(end.format(DATE_FORMAT)).build())
                .granularity("MONTHLY")
                .metrics(List.of(COST_METRIC.toString()));
        if (grouped) {
            request.groupBy(GroupDefinition.builder().type(GroupDefinitionType.DIMENSION).key(Dimension.SERVICE.toString()).build());
        }
        return client.getCostAndUsage(request.build());
    }

    private BigDecimal amount(GetCostAndUsageResponse response, int index) {
        return response.resultsByTime().size() > index ? amount(response.resultsByTime().get(index)) : BigDecimal.ZERO;
    }

    private BigDecimal amount(ResultByTime result) {
        var metric = result.total().get(COST_METRIC);
        String value = metric == null ? null : metric.amount();
        return new BigDecimal(value == null ? "0" : value).setScale(2, RoundingMode.HALF_UP);
    }

    private String monthLabel(ResultByTime result) {
        return YearMonth.parse(result.timePeriod().start().substring(0, 7)).getMonth()
                .getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH);
    }

    private List<ServiceCost> serviceCosts(GetCostAndUsageResponse response) {
        Map<String, BigDecimal> totals = response.resultsByTime().stream()
                .flatMap(result -> result.groups().stream())
                .collect(Collectors.groupingBy(group -> serviceName(group),
                        Collectors.reducing(BigDecimal.ZERO, group -> {
                            var metric = group.metrics().get(COST_METRIC);
                            String amount = metric == null ? null : metric.amount();
                            return new BigDecimal(amount == null ? "0" : amount);
                        }, BigDecimal::add)));
        BigDecimal ec2 = serviceValue(totals, "Amazon Elastic Compute Cloud - Compute");
        BigDecimal s3 = serviceValue(totals, "Amazon Simple Storage Service");
        BigDecimal rds = serviceValue(totals, "Amazon Relational Database Service");
        BigDecimal lambda = serviceValue(totals, "AWS Lambda");
        BigDecimal known = ec2.add(s3).add(rds).add(lambda);
        BigDecimal others = totals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add).subtract(known).max(BigDecimal.ZERO);
        return List.of(new ServiceCost("EC2", ec2), new ServiceCost("S3", s3), new ServiceCost("RDS", rds),
                new ServiceCost("Lambda", lambda), new ServiceCost("Others", others));
    }

    private String serviceName(software.amazon.awssdk.services.costexplorer.model.Group group) {
        return group.keys().isEmpty() ? "Others" : group.keys().get(0);
    }

    private BigDecimal serviceValue(Map<String, BigDecimal> values, String name) {
        return values.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name)).map(Map.Entry::getValue)
                .findFirst().orElse(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentage(BigDecimal value, BigDecimal total) {
        if (value == null || total == null || total.signum() == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }

    private BudgetUsage budgetUsage(BudgetsClient client, String accountId, BigDecimal actualSpend) {
        List<Budget> budgets = client.describeBudgets(DescribeBudgetsRequest.builder().accountId(accountId).build()).budgets();
        if (budgets.isEmpty()) return new BudgetUsage(false, null, null, actualSpend, CURRENCY, "NOT_CONFIGURED");
        Budget budget = budgets.get(0);
        BigDecimal limit = budget.budgetLimit().amount();
        String currency = budget.budgetLimit().unit();
        BigDecimal usage = limit.signum() == 0 ? null : actualSpend.multiply(BigDecimal.valueOf(100)).divide(limit, 2, RoundingMode.HALF_UP);
        String status = usage == null ? "NOT_CONFIGURED" : usage.compareTo(BigDecimal.valueOf(100)) >= 0 ? "OVER_BUDGET" : usage.compareTo(BigDecimal.valueOf(80)) >= 0 ? "NEAR_LIMIT" : "WITHIN_LIMIT";
        return new BudgetUsage(true, usage, limit, actualSpend, currency, status);
    }
}
