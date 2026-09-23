package com.cloudcostai.aws.service.impl;

import com.cloudcostai.aws.dto.response.AwsRecommendationResponse;
import com.cloudcostai.aws.dto.response.AwsRecommendationResponse.Recommendation;
import com.cloudcostai.aws.model.AwsBilling;
import com.cloudcostai.aws.model.AwsRecommendation;
import com.cloudcostai.aws.repository.AwsBillingRepository;
import com.cloudcostai.aws.repository.AwsRecommendationRepository;
import com.cloudcostai.aws.service.AwsConnectionSessionService;
import com.cloudcostai.aws.service.AwsRecommendationService;
import com.cloudcostai.aws.service.AwsBillingSourceResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AwsRecommendationServiceImpl implements AwsRecommendationService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private final AwsConnectionSessionService sessionService;
    private final AwsBillingRepository billingRepository;
    private final AwsRecommendationRepository recommendationRepository;
    private final AwsBillingSourceResolver billingSourceResolver;

    @Autowired
    public AwsRecommendationServiceImpl(AwsConnectionSessionService sessionService,
                                        AwsBillingRepository billingRepository,
                                        AwsRecommendationRepository recommendationRepository,
                                        AwsBillingSourceResolver billingSourceResolver) {
        this.sessionService = sessionService;
        this.billingRepository = billingRepository;
        this.recommendationRepository = recommendationRepository;
        this.billingSourceResolver = billingSourceResolver;
    }

    @Override
    @Transactional(readOnly = true)
    public AwsRecommendationResponse getRecommendations(String connectionToken) {
        var session = sessionService.getSession(connectionToken);
        List<AwsRecommendation> stored = recommendationRepository.findByAwsAccountIdOrderByCreatedAtDesc(session.accountId());
        String currency = billingRepository.findByAwsAccountId(session.accountId()).stream().map(AwsBilling::getCurrency)
                .filter(value -> value != null && !value.isBlank()).findFirst().orElse("USD");
        return response(session.accountId(), currency, stored);
    }

    @Override
    @Transactional(readOnly = true)
    public AwsRecommendationResponse getRecommendations(String connectionToken, String month) {
        var session = sessionService.getSession(connectionToken);
        YearMonth selected = parseMonth(month, YearMonth.now());
        List<AwsBilling> rows = billingSourceResolver == null ? billingRepository.findByAwsAccountId(session.accountId()).stream()
            .filter(row -> inRange(row, selected.atDay(1), selected.plusMonths(1).atDay(1))).toList()
            : billingSourceResolver.rows(session.accountId(), billingSourceResolver.resolve(session.accountId(), selected).dataSource(), selected.atDay(1), selected.plusMonths(1).atDay(1));
        String currency = rows.stream().map(AwsBilling::getCurrency).filter(value -> value != null && !value.isBlank()).findFirst().orElse("USD");
        if (rows.isEmpty()) return response(session.accountId(), currency, List.of(), selected, "NO_DATA", "No CUR billing data is available for this month.", null);
        return response(session.accountId(), currency, recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(session.accountId(), selected.toString()), selected, "SUCCESS", null, sum(rows));
    }

    @Override
    @Transactional
    public AwsRecommendationResponse generateRecommendations(String connectionToken) {
        return generateRecommendations(connectionToken, YearMonth.now().toString());
    }

    @Override
    @Transactional
    public AwsRecommendationResponse generateRecommendations(String connectionToken, String month) {
        var session = sessionService.getSession(connectionToken);
        YearMonth selected = parseMonth(month, YearMonth.now());
        LocalDate start = selected.atDay(1);
        LocalDate end = selected.plusMonths(1).atDay(1);
        List<AwsBilling> rows = billingSourceResolver == null ? billingRepository.findByAwsAccountId(session.accountId()).stream().filter(row -> inRange(row, start, end)).toList()
            : billingSourceResolver.rows(session.accountId(), billingSourceResolver.resolve(session.accountId(), selected).dataSource(), start, end);
        List<AwsBilling> currentRows = rows.stream().filter(row -> inRange(row, start, end)).toList();
        String currency = currentRows.stream().map(AwsBilling::getCurrency).filter(value -> value != null && !value.isBlank()).findFirst().orElse("USD");
        if (currentRows.isEmpty()) return response(session.accountId(), currency, List.of(), selected, "NO_DATA", "No CUR billing data is available for this month.", null);
        List<RecommendationDraft> drafts = analyze(rows, start, end, selected.minusMonths(1).atDay(1));
        for (RecommendationDraft draft : drafts) {
            AwsRecommendation entity = recommendationRepository.findByAwsAccountIdAndRecommendationKey(session.accountId(), draft.key())
                    .orElseGet(() -> new AwsRecommendation(session.accountId(), draft.key(), draft.title(), draft.category(), draft.severity(), draft.service(), draft.resourceId(), draft.region(), draft.currentCost(), draft.reason(), draft.recommendation(), draft.action(), draft.potentialSavings(), "New", selected.toString()));
            entity.update(draft.title(), draft.category(), draft.severity(), draft.service(), draft.resourceId(), draft.region(), draft.currentCost(), draft.reason(), draft.recommendation(), draft.action(), draft.potentialSavings(), entity.getStatus(), selected.toString());
            recommendationRepository.save(entity);
        }
        return response(session.accountId(), currency, recommendationRepository.findByAwsAccountIdAndBillingMonthOrderByCreatedAtDesc(session.accountId(), selected.toString()), selected, "SUCCESS", null, sum(currentRows));
    }

    @Override
    @Transactional
    public AwsRecommendationResponse updateStatus(String connectionToken, String recommendationId, String status) {
        return updateStatus(connectionToken, recommendationId, status, null);
    }

    @Override
    @Transactional
    public AwsRecommendationResponse updateStatus(String connectionToken, String recommendationId, String status, String month) {
        var session = sessionService.getSession(connectionToken);
        if (!List.of("New", "Applied", "Dismissed").contains(status)) throw new IllegalArgumentException("Status must be New, Applied, or Dismissed.");
        AwsRecommendation entity = recommendationRepository.findById(java.util.UUID.fromString(recommendationId))
                .filter(item -> session.accountId().equals(item.getAwsAccountId()))
                .orElseThrow(() -> new IllegalArgumentException("Recommendation was not found for this account."));
        entity.update(entity.getTitle(), entity.getCategory(), entity.getSeverity(), entity.getService(), entity.getResourceId(), entity.getRegion(), entity.getCurrentCost(), entity.getReason(), entity.getRecommendation(), entity.getAction(), entity.getPotentialSavings(), status);
        recommendationRepository.save(entity);
        return month == null || month.isBlank() ? getRecommendations(connectionToken) : getRecommendations(connectionToken, month);
    }

    private List<RecommendationDraft> analyze(List<AwsBilling> rows, LocalDate start, LocalDate end, LocalDate previousStart) {
        List<RecommendationDraft> result = new ArrayList<>();
        Map<String, BigDecimal> currentServices = totals(rows, start, end, this::service);
        Map<String, BigDecimal> previousServices = totals(rows, previousStart, start, this::service);
        rows.stream().filter(row -> inRange(row, start, end) && hasResource(row) && cost(row).compareTo(BigDecimal.ONE) >= 0)
                .collect(Collectors.groupingBy(AwsBilling::getResourceId, Collectors.reducing(ZERO, this::cost, BigDecimal::add))).entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed()).limit(5)
                .forEach(item -> result.add(draft("cost-resource-" + item.getKey(), "Expensive resource", "Cost", severity(item.getValue()), serviceFor(rows, item.getKey()), item.getKey(), regionFor(rows, item.getKey()), item.getValue(), "This resource is one of the highest-cost resources in the latest 30 days of stored CUR data.", "The stored billing rows show " + money(item.getValue()) + " in current cost for this resource.", "Review utilization and right-size, schedule, or retire the resource after confirming ownership.", null)));
        currentServices.forEach((service, current) -> {
            BigDecimal previous = previousServices.getOrDefault(service, ZERO);
            if (previous.signum() > 0 && current.compareTo(previous.multiply(BigDecimal.valueOf(1.5))) > 0) result.add(draft("spike-service-" + service, "Cost increase", "Trend", "High", displayService(service), null, "All regions", current, "Spend increased by at least 50% compared with the preceding 30-day stored billing period.", "PostgreSQL billing data changed from " + money(previous) + " to " + money(current) + ".", "Review recent deployments, usage types, and resource-level charges for this service.", null));
        });
        rows.stream().filter(row -> inRange(row, start, end) && hasResource(row) && row.getUsageAmount() != null && row.getUsageAmount().signum() == 0 && cost(row).signum() > 0)
                .collect(Collectors.groupingBy(AwsBilling::getResourceId, Collectors.reducing(ZERO, this::cost, BigDecimal::add))).entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed()).limit(5)
                .forEach(item -> result.add(draft("unused-resource-" + item.getKey(), "Unused billable resource", "Utilization", "Medium", serviceFor(rows, item.getKey()), item.getKey(), regionFor(rows, item.getKey()), item.getValue(), "Stored CUR rows report zero usage while this resource continues to incur charges.", "The evidence is the resource's zero usage amount and positive stored cost in the latest 30 days.", "Verify ownership and remove, schedule, or reconfigure the resource if it is no longer needed.", item.getValue())));
        currentServices.entrySet().stream().filter(item -> isHighCostService(item.getKey())).sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed()).limit(4)
                .forEach(item -> result.add(draft("cost-service-" + item.getKey(), "High service cost", "Cost", severity(item.getValue()), displayService(item.getKey()), null, "All regions", item.getValue(), "This service has material spend in the latest 30 days of stored billing data.", "The service total in PostgreSQL is " + money(item.getValue()) + " for the current analysis period.", "Review usage, sizing, and scheduling for the largest resources in this service.", null)));
        return result;
    }

    private RecommendationDraft draft(String key, String title, String category, String severity, String service, String resourceId, String region, BigDecimal currentCost, String reason, String recommendation, String action, BigDecimal potentialSavings) { return new RecommendationDraft(key, title, category, severity, service, resourceId, region, money(currentCost), reason, recommendation, action, potentialSavings == null ? null : money(potentialSavings)); }
    private AwsRecommendationResponse response(String accountId, String currency, List<AwsRecommendation> entities) { return new AwsRecommendationResponse(accountId, currency, entities.stream().map(this::toResponse).sorted(Comparator.comparingInt(this::priorityRank).reversed()).toList()); }
    private Recommendation toResponse(AwsRecommendation item) { return new Recommendation(item.getId().toString(), item.getService(), blank(item.getRegion(), "Unknown"), blank(item.getResourceId(), "Service total"), money(item.getCurrentCost()), item.getTitle(), item.getReason(), item.getRecommendation(), item.getAction(), item.getPotentialSavings() == null ? null : money(item.getPotentialSavings()), item.getSeverity(), item.getCategory(), item.getStatus(), item.getCreatedAt().toString(), resourcePath(item.getService()), item.getResourceId(), item.getReason(), item.getBillingMonth()); }
    private Map<String, BigDecimal> totals(List<AwsBilling> rows, LocalDate start, LocalDate end, Function<AwsBilling, String> key) { return rows.stream().filter(row -> inRange(row, start, end)).collect(Collectors.groupingBy(row -> blank(key.apply(row), "Unknown"), Collectors.reducing(ZERO, this::cost, BigDecimal::add))); }
    private boolean inRange(AwsBilling row, LocalDate start, LocalDate end) { LocalDate date = row.getUsageStartDate() == null ? row.getBillingPeriodStart() : row.getUsageStartDate(); return date != null && !date.isBefore(start) && date.isBefore(end); }
    private BigDecimal cost(AwsBilling row) { return row.getCostAmount() == null ? ZERO : row.getCostAmount(); }
    private boolean hasResource(AwsBilling row) { return row.getResourceId() != null && !row.getResourceId().isBlank(); }
    private String serviceFor(List<AwsBilling> rows, String resource) { return rows.stream().filter(row -> resource.equals(row.getResourceId())).map(this::service).findFirst().orElse("AWS resource"); }
    private String regionFor(List<AwsBilling> rows, String resource) { return rows.stream().filter(row -> resource.equals(row.getResourceId())).map(AwsBilling::getRegion).findFirst().orElse("Unknown"); }
    private String service(AwsBilling row) { return blank(row.getServiceName(), "Unknown"); }
    private boolean isHighCostService(String value) { String service = value.toLowerCase(); return service.contains("s3") || service.contains("simple storage") || service.contains("rds") || service.contains("relational database") || service.contains("lambda") || service.contains("ec2") || service.contains("elastic compute"); }
    private String displayService(String value) { String service = value.toLowerCase(); return service.contains("s3") || service.contains("simple storage") ? "S3" : service.contains("rds") || service.contains("relational database") ? "RDS" : service.contains("lambda") ? "Lambda" : service.contains("ec2") || service.contains("elastic compute") ? "EC2" : value; }
    private String resourcePath(String service) { return service.toLowerCase().contains("ec2") ? "/resources" : "/billing"; }
    private String severity(BigDecimal value) { return value.compareTo(BigDecimal.TEN) >= 0 ? "High" : "Medium"; }
    private String blank(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private BigDecimal money(BigDecimal value) { return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP); }
    private int priorityRank(Recommendation recommendation) { return "High".equals(recommendation.priority()) ? 3 : "Medium".equals(recommendation.priority()) ? 2 : 1; }
    private AwsRecommendationResponse response(String accountId, String currency, List<AwsRecommendation> entities, YearMonth month, String status, String message, BigDecimal totalSpend) { return new AwsRecommendationResponse(accountId, currency, entities.stream().map(this::toResponse).sorted(Comparator.comparingInt(this::priorityRank).reversed()).toList(), month.toString(), status, message, totalSpend == null ? null : money(totalSpend)); }
    private BigDecimal sum(List<AwsBilling> rows) { return rows.stream().map(this::cost).reduce(ZERO, BigDecimal::add); }
    private YearMonth parseMonth(String month, YearMonth fallback) { if (month == null || month.isBlank()) return fallback; try { return YearMonth.parse(month); } catch (RuntimeException exception) { throw new IllegalArgumentException("Invalid month format. Please use YYYY-MM."); } }
    private record RecommendationDraft(String key, String title, String category, String severity, String service, String resourceId, String region, BigDecimal currentCost, String reason, String recommendation, String action, BigDecimal potentialSavings) {}
}