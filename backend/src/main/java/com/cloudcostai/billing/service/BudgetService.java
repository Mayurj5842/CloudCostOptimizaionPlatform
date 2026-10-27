package com.cloudcostai.billing.service;

import com.cloudcostai.aws.model.AwsBilling;
import com.cloudcostai.aws.repository.AwsBillingRepository;
import com.cloudcostai.aws.service.AwsBillingSourceResolver;
import com.cloudcostai.billing.dto.AlertResponse;
import com.cloudcostai.billing.dto.BudgetRequest;
import com.cloudcostai.billing.dto.BudgetResponse;
import com.cloudcostai.billing.model.Budget;
import com.cloudcostai.billing.model.BudgetAlert;
import com.cloudcostai.billing.repository.BudgetAlertRepository;
import com.cloudcostai.billing.repository.BudgetRepository;
import com.cloudcostai.notifications.service.NotificationService;
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
import java.util.Objects;
import java.util.UUID;

@Service
@SuppressWarnings("null")
public class BudgetService {
    private final BudgetRepository budgetRepository;
    private final BudgetAlertRepository budgetAlertRepository;
    private final AwsBillingRepository awsBillingRepository;
    private final AwsBillingSourceResolver billingSourceResolver;
    private final NotificationService notificationService;

    @Autowired
    public BudgetService(BudgetRepository budgetRepository, BudgetAlertRepository budgetAlertRepository,
                         AwsBillingRepository awsBillingRepository,
                         AwsBillingSourceResolver billingSourceResolver,
                         NotificationService notificationService) {
        this.budgetRepository = budgetRepository;
        this.budgetAlertRepository = budgetAlertRepository;
        this.awsBillingRepository = awsBillingRepository;
        this.billingSourceResolver = billingSourceResolver;
        this.notificationService = notificationService;
    }

    public BudgetService(BudgetRepository budgetRepository, BudgetAlertRepository budgetAlertRepository,
                         AwsBillingRepository awsBillingRepository,
                         AwsBillingSourceResolver billingSourceResolver) {
        this(budgetRepository, budgetAlertRepository, awsBillingRepository, billingSourceResolver, null);
    }

    public List<BudgetResponse> getBudgetsForAccount(String awsAccountId) {
        return getBudgetsForAccount(awsAccountId, null);
    }

    public List<BudgetResponse> getBudgetsForAccount(String awsAccountId, String month) {
        YearMonth selectedMonth = parseMonth(month, YearMonth.now());
        List<Budget> budgets = budgetRepository.findByAwsAccountIdOrderByCreatedAtDesc(awsAccountId);
        return budgets.stream()
                .filter(budget -> YearMonth.from(budget.getStartDate()).equals(selectedMonth))
                .peek(budget -> {
                    evaluateAlerts(budget, selectedMonth, false);
                    evaluateSpendingSpike(budget, selectedMonth);
                })
                .map(budget -> toResponse(budget, selectedMonth))
                .toList();
    }

    public BudgetResponse getBudget(UUID id) {
        Budget budget = budgetRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Budget not found"));
        return toResponse(budget);
    }

    public BudgetResponse getBudget(UUID id, String awsAccountId) {
        return toResponse(findBudgetForAccount(id, awsAccountId), YearMonth.now());
    }

    @Transactional
    public BudgetResponse createBudget(String userId, BudgetRequest request) {
        validateRequest(request);
        if (budgetRepository.existsByAwsAccountIdAndName(request.awsAccountId(), request.name())) {
            throw new IllegalArgumentException("A budget with this name already exists for the selected AWS account");
        }

        List<Integer> thresholds = normalizeThresholds(request.thresholds());
        LocalDate now = LocalDate.now();
        YearMonth month = parseMonth(request.month(), YearMonth.from(now));
        Budget budget = new Budget(userId, request.awsAccountId(), request.name(), request.amount(),
                request.currency(), request.period(), month.atDay(1), month.plusMonths(1).atDay(1).minusDays(1),
            "ACTIVE", thresholds, request.serviceName(), request.enabled() == null || request.enabled(),
            normalizeSpikeThreshold(request.spikeThresholdPercent()));
        budgetRepository.save(budget);
        evaluateAlerts(budget, month, true);
        evaluateSpendingSpike(budget, month);
        return toResponse(budget, month);
    }

    @Transactional
    public BudgetResponse updateBudget(UUID id, BudgetRequest request) {
        validateRequest(request);
        Budget budget = budgetRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Budget not found"));
        List<Integer> thresholds = request.thresholds() == null || request.thresholds().isEmpty()
            ? budget.getAlertThresholdsList() : normalizeThresholds(request.thresholds());
        YearMonth month = parseMonth(request.month(), YearMonth.from(budget.getStartDate()));
        budget.update(request.name(), request.amount(), request.currency(), request.period(), month.atDay(1),
            month.plusMonths(1).atDay(1).minusDays(1), "ACTIVE", thresholds,
            request.serviceName() == null ? budget.getServiceName() : request.serviceName(),
            request.enabled() == null ? budget.isEnabled() : request.enabled(),
            request.spikeThresholdPercent() == null ? budget.getSpikeThresholdPercent() : normalizeSpikeThreshold(request.spikeThresholdPercent()));
        budgetRepository.save(budget);
        evaluateAlerts(budget, month, false);
        evaluateSpendingSpike(budget, month);
        return toResponse(budget, month);
    }

    @Transactional
    public BudgetResponse updateBudget(UUID id, String awsAccountId, BudgetRequest request) {
        validateRequest(request);
        Budget budget = findBudgetForAccount(id, awsAccountId);
        List<Integer> thresholds = request.thresholds() == null || request.thresholds().isEmpty()
            ? budget.getAlertThresholdsList() : normalizeThresholds(request.thresholds());
        YearMonth month = parseMonth(request.month(), YearMonth.from(budget.getStartDate()));
        budget.update(request.name(), request.amount(), request.currency(), request.period(),
            month.atDay(1), month.plusMonths(1).atDay(1).minusDays(1), "ACTIVE", thresholds,
            request.serviceName() == null ? budget.getServiceName() : request.serviceName(),
            request.enabled() == null ? budget.isEnabled() : request.enabled(),
            request.spikeThresholdPercent() == null ? budget.getSpikeThresholdPercent() : normalizeSpikeThreshold(request.spikeThresholdPercent()));
        budgetRepository.save(budget);
        evaluateAlerts(budget, month, false);
        evaluateSpendingSpike(budget, month);
        return toResponse(budget, month);
    }

    @Transactional
    public void deleteBudget(UUID id) {
        Budget budget = budgetRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Budget not found"));
        budgetRepository.delete(budget);
    }

    @Transactional
    public void deleteBudget(UUID id, String awsAccountId) {
        budgetRepository.delete(findBudgetForAccount(id, awsAccountId));
    }

    public BudgetResponse getBudgetUsage(UUID budgetId) {
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new IllegalArgumentException("Budget not found"));
        return toResponse(budget);
    }

    public BudgetResponse getBudgetUsage(UUID budgetId, String awsAccountId) {
        return toResponse(findBudgetForAccount(budgetId, awsAccountId), YearMonth.now());
    }

    public List<AlertResponse> getAlertsForAccount(String awsAccountId) {
        return getAlertsForAccount(awsAccountId, null);
    }

    public List<AlertResponse> getAlertsForAccount(String awsAccountId, String month) {
        return getAlertsForAccount(awsAccountId, month, null, null, null, null);
    }

    public List<String> getServicesForAccount(String awsAccountId, String month) {
        YearMonth selectedMonth = parseMonth(month, YearMonth.now());
        return billingSourceResolver.resolve(awsAccountId, selectedMonth).rows().stream()
                .map(row -> row.getServiceName())
                .filter(service -> service != null && !service.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public List<AlertResponse> getAlertsForAccount(String awsAccountId, String month, String severity,
                                                    String type, LocalDate from, LocalDate to) {
        YearMonth selectedMonth = parseMonth(month, YearMonth.now());
        List<Budget> budgets = budgetRepository.findByAwsAccountId(awsAccountId);
        budgets.stream().filter(Budget::isEnabled)
                .filter(budget -> YearMonth.from(budget.getStartDate()).equals(selectedMonth))
                .forEach(budget -> {
            evaluateAlerts(budget, selectedMonth, false);
            evaluateSpendingSpike(budget, selectedMonth);
        });
        return budgetAlertRepository.findByAwsAccountIdOrderByTriggeredAtDesc(awsAccountId).stream()
                .filter(alert -> selectedMonth.toString().equals(alert.getAlertPeriod()))
                .filter(alert -> severity == null || severity.isBlank() || severity.equalsIgnoreCase(alert.getSeverity()))
                .filter(alert -> type == null || type.isBlank() || type.equalsIgnoreCase(alert.getAlertType()))
                .filter(alert -> from == null || !alert.getTriggeredAt().toLocalDate().isBefore(from))
                .filter(alert -> to == null || !alert.getTriggeredAt().toLocalDate().isAfter(to))
                .map(this::toAlertResponse)
                .toList();
    }

    @Transactional
    public void markAlertRead(UUID alertId) {
        BudgetAlert alert = budgetAlertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found"));
        alert.markRead();
        budgetAlertRepository.save(alert);
    }

        @Transactional
        public void markAlertRead(UUID alertId, String awsAccountId) {
        BudgetAlert alert = budgetAlertRepository.findById(alertId)
            .orElseThrow(() -> new IllegalArgumentException("Alert not found"));
            if (!awsAccountId.equals(alert.getAwsAccountId())) {
                throw new IllegalArgumentException("Alert not found");
            }
        alert.markRead();
        budgetAlertRepository.save(alert);
        }

    @Transactional
    public void evaluateAlerts(Budget budget, boolean createOnStartup) {
        evaluateAlerts(budget, YearMonth.from(budget.getStartDate()), createOnStartup);
    }

    public void evaluateAlerts(Budget budget, YearMonth selectedMonth, boolean createOnStartup) {
        if (!budget.isEnabled()) return;
        BigDecimal actualSpend = getActualSpend(budget, selectedMonth);
        if (actualSpend == null) {
            return;
        }
        if (budget.getAmount() == null || budget.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal usagePercentage = actualSpend.divide(budget.getAmount(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        budget.setStatus(calculateStatus(usagePercentage));
        budgetRepository.save(budget);

        for (Integer threshold : budget.getAlertThresholdsList()) {
            boolean triggered = usagePercentage.compareTo(BigDecimal.valueOf(threshold)) >= 0;
            if (!triggered) {
                continue;
            }
            String alertPeriod = selectedMonth.toString();
                String eventKey = "BUDGET:" + budget.getId() + ":" + threshold + ":" + alertPeriod;
                boolean exists = budgetAlertRepository.existsByBudgetIdAndThresholdPercentAndAlertPeriod(budget.getId(), threshold, alertPeriod)
                    || budgetAlertRepository.existsByEventKey(eventKey);
            if (exists) {
                continue;
            }
            String message = buildAlertMessage(threshold, budget, actualSpend);
            String alertType = threshold >= 100 ? "BUDGET_EXCEEDED" : "BUDGET_THRESHOLD";
            String severity = threshold >= 100 ? "CRITICAL" : threshold >= 75 ? "WARNING" : "INFO";
            String title = threshold >= 100 ? "Budget exceeded" : threshold + "% budget threshold reached";
            saveAlert(budget, threshold, alertPeriod, message, alertType, severity, title,
                    actualSpend, budget.getAmount(), null, null, eventKey);
        }
    }

    public static String calculateStatus(BigDecimal usagePercentage) {
        if (usagePercentage.compareTo(BigDecimal.valueOf(100)) >= 0) return "EXCEEDED";
        if (usagePercentage.compareTo(BigDecimal.valueOf(90)) >= 0) return "CRITICAL";
        if (usagePercentage.compareTo(BigDecimal.valueOf(75)) >= 0) return "WARNING";
        if (usagePercentage.compareTo(BigDecimal.valueOf(50)) >= 0) return "NORMAL";
        return "SAFE";
    }

    private BudgetResponse toResponse(Budget budget) {
        return toResponse(budget, YearMonth.now());
    }

    private BudgetResponse toResponse(Budget budget, YearMonth selectedMonth) {
        LocalDate start = selectedMonth.atDay(1);
        LocalDate end = selectedMonth.plusMonths(1).atDay(1);
        BigDecimal actualSpend = getActualSpend(budget, selectedMonth);
        BigDecimal usagePercentage = actualSpend == null ? null : budget.getAmount().compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : actualSpend.multiply(BigDecimal.valueOf(100)).divide(budget.getAmount(), 4, RoundingMode.HALF_UP);
        BigDecimal remainingAmount = actualSpend == null ? null : budget.getAmount().subtract(actualSpend);
        Forecast forecast = forecast(budget, selectedMonth, actualSpend);
        return new BudgetResponse(
                budget.getId(),
                budget.getName(),
                budget.getAwsAccountId(),
                budget.getAmount(),
                actualSpend,
                remainingAmount,
                usagePercentage,
                budget.getCurrency(),
                actualSpend == null ? "NO_BILLING_DATA" : determineStatusFromUsage(usagePercentage),
                budget.getPeriod(),
                budget.getAlertThresholdsList(),
                budget.getCreatedAt(),
                budget.getUpdatedAt(),
                budget.getServiceName(),
                budget.isEnabled(),
                forecast.amount(),
                forecast.variance(),
                forecast.available(),
                forecast.message(),
                budget.getSpikeThresholdPercent()
        );
    }

    private AlertResponse toAlertResponse(BudgetAlert alert) {
        return new AlertResponse(alert.getId(), alert.getBudgetId(), alert.getAlertType(),
                alert.getThresholdPercent(), alert.getMessage(), alert.getActualSpend(), alert.getBudgetAmount(),
                alert.getCurrency(), "READ".equals(alert.getStatus()), alert.getTriggeredAt(), alert.getSeverity(),
                alert.getTitle(), alert.getBudgetName(), alert.getServiceName(), alert.getAlertPeriod(), alert.getComparisonPeriod(),
                alert.getPercentageChange());
    }

    private String determineStatusFromUsage(BigDecimal usagePercentage) {
        if (usagePercentage.compareTo(BigDecimal.valueOf(100)) >= 0) return "EXCEEDED";
        if (usagePercentage.compareTo(BigDecimal.valueOf(90)) >= 0) return "CRITICAL";
        if (usagePercentage.compareTo(BigDecimal.valueOf(75)) >= 0) return "WARNING";
        if (usagePercentage.compareTo(BigDecimal.valueOf(50)) >= 0) return "NORMAL";
        return "SAFE";
    }

    private BigDecimal getActualSpend(String awsAccountId, LocalDate startDate, LocalDate endDate) {
        List<AwsBilling> rows = billingSourceResolver.resolve(awsAccountId, startDate, endDate).rows();
        return sumRows(rows);
    }

    private BigDecimal getActualSpend(Budget budget, YearMonth month) {
        return sumRows(billingRows(budget, month));
    }

    private List<AwsBilling> billingRows(Budget budget, YearMonth month) {
        return billingSourceResolver.resolve(budget.getAwsAccountId(), month).rows().stream()
                .filter(row -> !row.isEstimated())
                .filter(row -> budget.getServiceName() == null || row.getServiceName() != null
                        && budget.getServiceName().equalsIgnoreCase(row.getServiceName().trim()))
                .toList();
    }

    private BigDecimal sumRows(List<AwsBilling> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        BigDecimal total = rows.stream().map(AwsBilling::getCostAmount).filter(cost -> cost != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.setScale(2, RoundingMode.HALF_UP);
    }

        private Forecast forecast(Budget budget, YearMonth month, BigDecimal actualSpend) {
        if (actualSpend == null) return new Forecast(null, null, false, "No billing data is available for this month.");
        if (!month.equals(YearMonth.from(LocalDate.now()))) {
            return new Forecast(null, null, false, "Forecast estimates are available for the current billing month only.");
        }
        List<AwsBilling> rows = billingRows(budget, month).stream()
            .filter(row -> billingDate(row) != null && !billingDate(row).isAfter(LocalDate.now()))
            .toList();
        long observedDays = rows.stream().map(this::billingDate).distinct().count();
        BigDecimal observedSpend = sumRows(rows);
        return calculateForecast(observedSpend, budget.getAmount(), observedDays, month, LocalDate.now());
        }

        static Forecast calculateForecast(BigDecimal actualSpend, BigDecimal budgetAmount, long observedDays,
                          YearMonth month, LocalDate today) {
        if (actualSpend == null) return new Forecast(null, null, false, "No billing data is available for this month.");
        if (!month.equals(YearMonth.from(today))) {
            return new Forecast(null, null, false, "Forecast estimates are available for the current billing month only.");
        }
        int elapsedDays = today.getDayOfMonth();
        if (observedDays < 3 || elapsedDays < 3) {
            return new Forecast(null, null, false, "Insufficient current-month billing history for a reliable estimate.");
        }
        BigDecimal projected = actualSpend.multiply(BigDecimal.valueOf(month.lengthOfMonth()))
            .divide(BigDecimal.valueOf(elapsedDays), 2, RoundingMode.HALF_UP);
        return new Forecast(projected, projected.subtract(budgetAmount).setScale(2, RoundingMode.HALF_UP),
            true, "Estimate based on current-month actual spending.");
        }

        private void evaluateSpendingSpike(Budget budget, YearMonth month) {
        if (!budget.isEnabled() || month.isAfter(YearMonth.from(LocalDate.now()))) return;
        LocalDate today = LocalDate.now();
        int comparedDays = Math.min(month.equals(YearMonth.from(today)) ? today.getDayOfMonth() : month.lengthOfMonth(),
            month.minusMonths(1).lengthOfMonth());
        if (comparedDays < 3) return;

        List<AwsBilling> currentRows = billingRows(budget, month).stream()
            .filter(row -> billingDate(row) != null && billingDate(row).getDayOfMonth() <= comparedDays)
            .toList();
        List<AwsBilling> previousRows = billingSourceResolver.resolve(budget.getAwsAccountId(), month.minusMonths(1)).rows().stream()
            .filter(row -> !row.isEstimated())
            .filter(row -> budget.getServiceName() == null || row.getServiceName() != null
                && budget.getServiceName().equalsIgnoreCase(row.getServiceName().trim()))
            .filter(row -> billingDate(row) != null && billingDate(row).getDayOfMonth() <= comparedDays)
            .toList();
        if (currentRows.stream().map(this::billingDate).distinct().count() < 3
            || previousRows.stream().map(this::billingDate).distinct().count() < 3) return;

        BigDecimal currentSpend = sumRows(currentRows);
        BigDecimal previousSpend = sumRows(previousRows);
        if (currentSpend == null || previousSpend == null || previousSpend.signum() <= 0) return;
        BigDecimal change = currentSpend.subtract(previousSpend).multiply(BigDecimal.valueOf(100))
            .divide(previousSpend, 2, RoundingMode.HALF_UP);
        int threshold = normalizeSpikeThreshold(budget.getSpikeThresholdPercent());
        if (change.compareTo(BigDecimal.valueOf(threshold)) < 0) return;

        String alertPeriod = month.toString();
        String eventKey = "SPIKE:" + budget.getId() + ":" + threshold + ":" + alertPeriod;
        if (budgetAlertRepository.existsByEventKey(eventKey)) return;
        String comparison = month.minusMonths(1) + " days 1-" + comparedDays;
        String message = "Spending increased " + change.toPlainString() + "% versus " + comparison
            + " (" + money(currentSpend, budget.getCurrency()) + " vs "
            + money(previousSpend, budget.getCurrency()) + "); alert threshold: " + threshold + "%.";
        saveAlert(budget, threshold, alertPeriod, message, "SPENDING_SPIKE", "WARNING",
            "Spending spike detected", currentSpend, budget.getAmount(), comparison, change, eventKey);
        }

        private void saveAlert(Budget budget, Integer threshold, String alertPeriod, String message, String type,
                   String severity, String title, BigDecimal actualSpend, BigDecimal budgetAmount,
                   String comparisonPeriod, BigDecimal percentageChange, String eventKey) {
        BudgetAlert alert = new BudgetAlert(budget.getId(), threshold, alertPeriod, "UNREAD", message, type,
            severity, title, budget.getName(), budget.getAwsAccountId(), budget.getServiceName(), actualSpend,
            budgetAmount, budget.getCurrency(), comparisonPeriod, percentageChange, eventKey);
        BudgetAlert saved = budgetAlertRepository.save(alert);
        if (notificationService != null) notificationService.sendBudgetAlert(saved);
        }

        private String money(BigDecimal amount, String currency) {
        return currency + " " + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }

        record Forecast(BigDecimal amount, BigDecimal variance, boolean available, String message) {}

    private YearMonth parseMonth(String month, YearMonth fallback) {
        if (month == null || month.isBlank()) {
            return fallback;
        }
        try {
            return YearMonth.parse(month);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid month format. Please use YYYY-MM, for example 2026-09.");
        }
    }

    private LocalDate periodStart(Budget budget) {
        if ("MONTHLY".equalsIgnoreCase(budget.getPeriod())) {
            return YearMonth.from(budget.getStartDate()).atDay(1);
        }
        return budget.getStartDate();
    }

    private LocalDate periodEndExclusive(Budget budget) {
        if ("MONTHLY".equalsIgnoreCase(budget.getPeriod())) {
            return YearMonth.from(budget.getStartDate()).plusMonths(1).atDay(1);
        }
        return budget.getEndDate().plusDays(1);
    }

    private List<Integer> normalizeThresholds(List<Integer> thresholds) {
        if (thresholds == null || thresholds.isEmpty()) return List.of(50, 75, 90, 100);
        if (thresholds.stream().anyMatch(v -> v == null || v <= 0 || v > 100)) {
            throw new IllegalArgumentException("Alert thresholds must be between 1 and 100.");
        }
        return thresholds.stream().distinct().sorted().toList();
    }

    private void validateRequest(BudgetRequest request) {
        if (request.name() == null || request.name().isBlank()) throw new IllegalArgumentException("Budget name cannot be empty");
        if (request.name().trim().length() > 200) throw new IllegalArgumentException("Budget name must be 200 characters or fewer");
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("Budget amount must be greater than 0");
        if (request.awsAccountId() == null || request.awsAccountId().isBlank()) throw new IllegalArgumentException("Valid AWS account must be selected");
        if (request.currency() == null || !request.currency().matches("[A-Za-z]{3}")) throw new IllegalArgumentException("Currency must be a three-letter code");
        if (!"MONTHLY".equalsIgnoreCase(request.period())) throw new IllegalArgumentException("Only MONTHLY budgets are supported.");
        if (request.serviceName() != null && request.serviceName().trim().length() > 255) throw new IllegalArgumentException("Service name must be 255 characters or fewer");
        normalizeThresholds(request.thresholds());
        normalizeSpikeThreshold(request.spikeThresholdPercent());
        parseMonth(request.month(), YearMonth.now());
    }

    private Integer normalizeSpikeThreshold(Integer threshold) {
        int normalized = threshold == null ? 50 : threshold;
        if (normalized < 1 || normalized > 500) {
            throw new IllegalArgumentException("Spending spike threshold must be between 1 and 500 percent.");
        }
        return normalized;
    }

    private LocalDate billingDate(AwsBilling row) {
        return row.getUsageStartDate() == null ? row.getBillingPeriodStart() : row.getUsageStartDate();
    }

    private String buildAlertMessage(Integer threshold, Budget budget, BigDecimal actualSpend) {
        String statusText = threshold >= 100 ? "Budget exceeded" : "Budget alert";
        BigDecimal remaining = budget.getAmount().subtract(actualSpend);
        return statusText + ": your AWS spend reached " + threshold + "% of the monthly budget. Current spend: " + actualSpend + ", budget: " + budget.getAmount() + ", remaining: " + remaining;
    }

    private Budget findBudgetForAccount(UUID budgetId, String awsAccountId) {
        return budgetRepository.findById(budgetId)
                .filter(budget -> budget.getAwsAccountId().equals(awsAccountId))
                .orElseThrow(() -> new IllegalArgumentException("Budget not found"));
    }
}
