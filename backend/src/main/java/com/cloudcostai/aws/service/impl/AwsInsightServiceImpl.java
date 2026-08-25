package com.cloudcostai.aws.service.impl;

import com.cloudcostai.aws.dto.response.AwsInsightResponse;
import com.cloudcostai.aws.dto.response.AwsInsightResponse.Insight;
import com.cloudcostai.aws.model.AwsBilling;
import com.cloudcostai.aws.repository.AwsBillingRepository;
import com.cloudcostai.aws.service.AwsConnectionSessionService;
import com.cloudcostai.aws.service.AwsInsightService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    public AwsInsightServiceImpl(AwsConnectionSessionService sessionService,
                                 AwsBillingRepository billingRepository) {
        this.sessionService = sessionService;
        this.billingRepository = billingRepository;
    }

    @Override
    public AwsInsightResponse getInsights(String connectionToken) {
        var session = sessionService.getSession(connectionToken);
        List<AwsBilling> rows = billingRepository.findByAwsAccountId(session.accountId());
        LocalDate today = LocalDate.now();
        LocalDate currentStart = today.minusDays(30);
        LocalDate previousStart = today.minusDays(60);
        List<Insight> insights = new ArrayList<>();

        Map<String, BigDecimal> currentServices = totals(rows, currentStart, today, AwsBilling::getServiceName);
        Map<String, BigDecimal> previousServices = totals(rows, previousStart, currentStart, AwsBilling::getServiceName);
        Map<String, BigDecimal> currentResources = totals(rows, currentStart, today, row -> resourceName(row));
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
                    "Cloud spend increased sharply.", "The last 30 days are more than 25% above the preceding 30 days.", currentTotal.subtract(previousTotal), "Compare daily CUR line items and investigate newly introduced services or resources.", "/billing"));
        }

        currentServices.forEach((service, cost) -> {
            BigDecimal previous = previousServices.getOrDefault(service, ZERO);
            if (previous.signum() > 0 && cost.compareTo(previous.multiply(BigDecimal.valueOf(1.5))) > 0 && cost.subtract(previous).compareTo(BigDecimal.ONE) >= 0) {
                insights.add(insight("spike-" + service, "High", "Cost spike", service, "All regions", "Service total", cost,
                        "Service spend rose unexpectedly.", "Current spend is at least 50% above the previous 30-day period.", cost.subtract(previous), "Inspect the service's CUR usage type and resource-level charges.", "/billing"));
            }
        });

        rows.stream().filter(row -> row.getUsageStartDate() != null && !row.getUsageStartDate().isBefore(currentStart) && row.getUsageAmount() != null && row.getUsageAmount().signum() == 0 && row.getCostAmount().compareTo(BigDecimal.ZERO) > 0)
                .collect(java.util.stream.Collectors.groupingBy(this::resourceName, java.util.stream.Collectors.reducing(ZERO, AwsBilling::getCostAmount, BigDecimal::add)))
                .entrySet().stream().sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed()).limit(5)
                .forEach(entry -> insights.add(insight("unused-" + entry.getKey(), "Medium", "Underutilized resource", serviceFor(rows, entry.getKey()), regionFor(rows, entry.getKey()), entry.getKey(), entry.getValue(),
                        "A billable resource has no reported usage.", "CUR reports zero usage for this resource in the last 30 days while charges continue.", entry.getValue(), "Verify the resource is still required; stop, schedule, or remove it if it is not serving workloads.", "/resources")));

        if (insights.isEmpty() && !rows.isEmpty()) {
            insights.add(insight("steady-spend", "Low", "Spending pattern", "AWS account", "All regions", "Account total", currentTotal,
                    "No material anomaly was detected.", "Current CUR spend is within the normal comparison range.", ZERO, "Continue monitoring service and resource-level spend.", "/billing"));
        }
        return new AwsInsightResponse(session.accountId(), rows.isEmpty() ? "USD" : rows.get(0).getCurrency(), LocalDateTime.now().toString(), insights.stream().sorted(Comparator.comparingInt(this::severityRank).reversed()).toList());
    }

    private Map<String, BigDecimal> totals(List<AwsBilling> rows, LocalDate start, LocalDate end, java.util.function.Function<AwsBilling, String> key) {
        return rows.stream().filter(row -> row.getUsageStartDate() != null && !row.getUsageStartDate().isBefore(start) && row.getUsageStartDate().isBefore(end))
                .collect(java.util.stream.Collectors.groupingBy(row -> blank(key.apply(row), "Unknown"), java.util.stream.Collectors.reducing(ZERO, AwsBilling::getCostAmount, BigDecimal::add)));
    }

    private Insight insight(String id, String severity, String category, String service, String region, String resource, BigDecimal cost, String problem, String reason, BigDecimal impact, String action, String path) {
        return new Insight(id, severity, category, service, blank(region, "Unknown"), resource, money(cost), problem, reason, money(impact), action, path);
    }

    private String resourceName(AwsBilling row) { return blank(row.getResourceId(), blank(row.getProductName(), row.getServiceName())); }
    private String serviceFor(List<AwsBilling> rows, String resource) { return rows.stream().filter(row -> resource.equals(resourceName(row))).map(AwsBilling::getServiceName).findFirst().orElse("Unknown"); }
    private String regionFor(List<AwsBilling> rows, String resource) { return rows.stream().filter(row -> resource.equals(resourceName(row))).map(AwsBilling::getRegion).findFirst().orElse("Unknown"); }
    private String blank(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private BigDecimal money(BigDecimal value) { return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP); }
    private int severityRank(Insight insight) { return "High".equals(insight.severity()) ? 3 : "Medium".equals(insight.severity()) ? 2 : 1; }
}