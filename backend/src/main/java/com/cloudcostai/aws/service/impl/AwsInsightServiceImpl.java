package com.cloudcostai.aws.service.impl;

import com.cloudcostai.aws.dto.response.AwsInsightResponse;
import com.cloudcostai.aws.dto.response.AwsInsightResponse.Insight;
import com.cloudcostai.aws.dto.response.AwsRecommendationResponse.Recommendation;
import com.cloudcostai.aws.service.AwsRecommendationService;
import com.cloudcostai.aws.service.AwsResourceService;
import com.cloudcostai.aws.service.AwsBillingSourceResolver;
import com.cloudcostai.aws.model.AwsBilling;
import com.cloudcostai.aws.repository.AwsBillingRepository;
import com.cloudcostai.aws.service.AwsConnectionSessionService;
import com.cloudcostai.aws.service.AwsInsightService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AwsInsightServiceImpl implements AwsInsightService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private final AwsConnectionSessionService sessionService;
    private final AwsBillingRepository billingRepository;
    private final AwsRecommendationService recommendationService;
    private final AwsResourceService resourceService;
    private final AwsBillingSourceResolver billingSourceResolver;

    @Autowired
    public AwsInsightServiceImpl(AwsConnectionSessionService sessionService,
                                 AwsBillingRepository billingRepository,
                                 AwsRecommendationService recommendationService,
                                 AwsResourceService resourceService,
                                 AwsBillingSourceResolver billingSourceResolver) {
        this.sessionService = sessionService;
        this.billingRepository = billingRepository;
        this.recommendationService = recommendationService;
        this.resourceService = resourceService;
        this.billingSourceResolver = billingSourceResolver;
    }

    public AwsInsightServiceImpl(AwsConnectionSessionService sessionService,
                                 AwsBillingRepository billingRepository) {
        this(sessionService, billingRepository, null, null, null);
    }

    @Override
    public AwsInsightResponse getInsights(String connectionToken, String month) {
        var session = sessionService.getSession(connectionToken);
        DateRange selected = dateRange(month);
        YearMonth selectedMonth = YearMonth.from(selected.start());
        String dataSource = billingSourceResolver == null ? "CUR_2_0" : billingSourceResolver.resolve(session.accountId(), selectedMonth).dataSource();
        LocalDate currentStart = selected.start();
        LocalDate currentEnd = selected.end();
        LocalDate previousStart = currentStart.minusMonths(1);
        List<AwsBilling> rows = sourceRows(session.accountId(), selectedMonth, previousStart, currentEnd);
        List<AwsBilling> currentRows = rows.stream().filter(row -> inRange(row, currentStart, currentEnd)).toList();
        String currency = rows.stream().map(AwsBilling::getCurrency).filter(value -> value != null && !value.isBlank()).findFirst().orElse("USD");
        if (currentRows.isEmpty()) {
            return new AwsInsightResponse(session.accountId(), currency, LocalDateTime.now().toString(), List.of(),
                    List.of(), "NO_CUR_DATA", dataSource, currentStart.toString(), currentEnd.minusDays(1).toString(), null, null,
                    "No billing data is available for this period.");
        }
        List<Insight> insights = new ArrayList<>();

        Map<String, BigDecimal> currentServices = totals(currentRows, currentStart, currentEnd, AwsBilling::getServiceName);
        Map<String, BigDecimal> previousServices = totals(rows, previousStart, currentStart, AwsBilling::getServiceName);
        BigDecimal currentTotal = currentServices.values().stream().reduce(ZERO, BigDecimal::add);
        BigDecimal previousTotal = previousServices.values().stream().reduce(ZERO, BigDecimal::add);

        currentServices.entrySet().stream().sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed()).limit(5)
                .forEach(entry -> {
                    BigDecimal share = currentTotal.signum() == 0 ? ZERO : entry.getValue().divide(currentTotal, 4, RoundingMode.HALF_UP);
                    if (entry.getValue().compareTo(BigDecimal.ONE) >= 0 || share.compareTo(BigDecimal.valueOf(.25)) >= 0) {
                        insights.add(insight("driver-" + entry.getKey(), share.compareTo(BigDecimal.valueOf(.5)) >= 0 ? "High" : "Medium", "Top cost driver", entry.getKey(), "All regions", "Service total", entry.getValue(),
                                "This service is a major contributor to current spend.", "It represents " + share.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP) + "% of the last 30 days.", entry.getValue().multiply(BigDecimal.valueOf(.10)), "Review the service breakdown and right-size its largest resources.", "/resources"));
                    }
                });

        if (previousTotal.signum() > 0 && currentTotal.compareTo(previousTotal.multiply(BigDecimal.valueOf(1.25))) > 0) {
            insights.add(insight("account-increase", "High", "Sudden increase", "AWS account", "All regions", "Account total", currentTotal,
                    "Cloud spend increased sharply.", "The last 30 days are more than 25% above the preceding 30 days.", currentTotal.subtract(previousTotal), "Compare daily stored billing rows and investigate newly introduced services or resources.", "/billing"));
        }

        currentServices.forEach((service, cost) -> {
            BigDecimal previous = previousServices.getOrDefault(service, ZERO);
            if (previous.signum() > 0 && cost.compareTo(previous.multiply(BigDecimal.valueOf(1.5))) > 0 && cost.subtract(previous).compareTo(BigDecimal.ONE) >= 0) {
                insights.add(insight("spike-" + service, "High", "Cost spike", service, "All regions", "Service total", cost,
                        "Service spend rose unexpectedly.", "Current spend is at least 50% above the previous 30-day period.", cost.subtract(previous), "Inspect the service's stored usage types and resource-level charges.", "/billing"));
            }
        });

        currentRows.stream().filter(row -> row.getUsageAmount() != null && row.getUsageAmount().signum() == 0 && cost(row).signum() > 0)
            .collect(java.util.stream.Collectors.groupingBy(this::resourceName, java.util.stream.Collectors.reducing(ZERO, this::cost, BigDecimal::add)))
                .entrySet().stream().sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed()).limit(5)
                .forEach(entry -> insights.add(insight("unused-" + entry.getKey(), "Medium", "Underutilized resource", serviceFor(rows, entry.getKey()), regionFor(rows, entry.getKey()), entry.getKey(), entry.getValue(),
                        "A billable resource has no reported usage.", "Stored billing reports zero usage for this resource while charges continue.", entry.getValue(), "Verify the resource is still required; stop, schedule, or remove it if it is not serving workloads.", "/resources")));

        if (insights.isEmpty() && !rows.isEmpty()) {
            insights.add(insight("steady-spend", "Low", "Spending pattern", "AWS account", "All regions", "Account total", currentTotal,
                    "No material anomaly was detected.", "Current stored billing spend is within the normal comparison range.", ZERO, "Continue monitoring service and resource-level spend.", "/billing"));
        }
        addResourceFindings(connectionToken, insights);
        List<Recommendation> recommendations = recommendations(connectionToken, selected);
        String lastUpdated = currentRows.stream().map(AwsBilling::getUpdatedAt).filter(value -> value != null).max(Comparator.naturalOrder()).map(Object::toString).orElse(null);
        return new AwsInsightResponse(session.accountId(), currency, LocalDateTime.now().toString(), insights.stream().sorted(Comparator.comparingInt(this::severityRank).reversed()).toList(), recommendations,
            "SUCCESS", dataSource, currentStart.toString(), currentEnd.minusDays(1).toString(), lastUpdated, currentTotal, null);
    }

    private Map<String, BigDecimal> totals(List<AwsBilling> rows, LocalDate start, LocalDate end, java.util.function.Function<AwsBilling, String> key) {
        return rows.stream().filter(row -> inRange(row, start, end))
                .collect(java.util.stream.Collectors.groupingBy(row -> blank(key.apply(row), "Other AWS services"), java.util.stream.Collectors.reducing(ZERO, this::cost, BigDecimal::add)));
    }

    private Insight insight(String id, String severity, String category, String service, String region, String resource, BigDecimal cost, String problem, String reason, BigDecimal impact, String action, String path) {
        return new Insight(id, severity, category, service, blank(region, "Unknown"), resource, money(cost), problem, reason, money(impact), action, path);
    }

    private String resourceName(AwsBilling row) { return blank(row.getResourceId(), blank(row.getProductName(), blank(row.getServiceName(), "Unknown"))); }
    private String serviceFor(List<AwsBilling> rows, String resource) { return rows.stream().filter(row -> resource.equals(resourceName(row))).map(row -> blank(row.getServiceName(), "Other AWS services")).findFirst().orElse("Other AWS services"); }
    private String regionFor(List<AwsBilling> rows, String resource) { return rows.stream().filter(row -> resource.equals(resourceName(row))).map(row -> blank(row.getRegion(), "Unknown")).findFirst().orElse("Unknown"); }
    private String blank(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private BigDecimal money(BigDecimal value) { return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP); }
    private BigDecimal cost(AwsBilling row) { return row.getCostAmount() == null ? ZERO : row.getCostAmount(); }
    private boolean inRange(AwsBilling row, LocalDate start, LocalDate end) {
        LocalDate date = row.getUsageStartDate() == null ? row.getBillingPeriodStart() : row.getUsageStartDate();
        return date != null && !date.isBefore(start) && date.isBefore(end);
    }
    private int severityRank(Insight insight) { return "High".equals(insight.severity()) ? 3 : "Medium".equals(insight.severity()) ? 2 : 1; }

    private List<Recommendation> recommendations(String connectionToken, DateRange selected) {
        if (recommendationService == null) return List.of();
        try {
            return recommendationService.getRecommendations(connectionToken, selected.start().toString().substring(0, 7)).recommendations();
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private void addResourceFindings(String connectionToken, List<Insight> insights) {
        if (resourceService == null) return;
        try {
            var resources = resourceService.discoverResources(connectionToken);
            resources.ec2Instances().stream().filter(item -> "stopped".equalsIgnoreCase(item.state())).limit(5)
                    .forEach(item -> insights.add(insight("stopped-ec2-" + item.instanceId(), "Medium", "Stopped EC2 instance", "EC2", item.region(), item.instanceId(), null,
                            "An EC2 instance is stopped.", "The live AWS resource state reports this instance as stopped.", null,
                            "Confirm ownership and terminate or schedule the instance if it is no longer required.", "/resources")));
            resources.ebsVolumes().stream().filter(item -> item.attachedInstanceId() == null && "available".equalsIgnoreCase(item.state())).limit(5)
                    .forEach(item -> insights.add(insight("unused-ebs-" + item.volumeId(), "Medium", "Unused EBS volume", "EBS", null, item.volumeId(), null,
                            "An EBS volume is unattached.", "The live AWS resource state reports no attached instance.", null,
                            "Verify ownership and delete the volume if it is no longer required.", "/resources")));
        } catch (RuntimeException exception) {
            // CUR insights remain valid when the optional live resource lookup is unavailable.
        }
    }

    private DateRange dateRange(String month) {
        if (month == null || month.isBlank()) {
            YearMonth current = YearMonth.now();
            return new DateRange(current.atDay(1), current.plusMonths(1).atDay(1));
        }
        try {
            YearMonth selected = YearMonth.parse(month);
            return new DateRange(selected.atDay(1), selected.plusMonths(1).atDay(1));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid billing month. Please use YYYY-MM.");
        }
    }

    private List<AwsBilling> sourceRows(String accountId, YearMonth month, LocalDate start, LocalDate end) {
        if (billingSourceResolver != null) return billingSourceResolver.resolve(accountId, start, end).rows();
        return billingRepository.findByAwsAccountId(accountId).stream().filter(row -> inRange(row, start, end)).toList();
    }

    private record DateRange(LocalDate start, LocalDate end) {}
}