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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@SuppressWarnings("null")
public class BudgetService {
    private final BudgetRepository budgetRepository;
    private final BudgetAlertRepository budgetAlertRepository;
    private final AwsBillingRepository awsBillingRepository;
    private final AwsBillingSourceResolver billingSourceResolver;

    public BudgetService(BudgetRepository budgetRepository, BudgetAlertRepository budgetAlertRepository,
                         AwsBillingRepository awsBillingRepository,
                         AwsBillingSourceResolver billingSourceResolver) {
        this.budgetRepository = budgetRepository;
        this.budgetAlertRepository = budgetAlertRepository;
        this.awsBillingRepository = awsBillingRepository;
        this.billingSourceResolver = billingSourceResolver;
    }

    public List<BudgetResponse> getBudgetsForAccount(String awsAccountId) {
        return getBudgetsForAccount(awsAccountId, null);
    }

    public List<BudgetResponse> getBudgetsForAccount(String awsAccountId, String month) {
        YearMonth selectedMonth = parseMonth(month, YearMonth.now());
        List<Budget> budgets = budgetRepository.findByAwsAccountIdOrderByCreatedAtDesc(awsAccountId);
        budgets.forEach(budget -> evaluateAlerts(budget, selectedMonth, false));
        return budgets.stream()
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
                "ACTIVE", thresholds);
        budgetRepository.save(budget);
        evaluateAlerts(budget, month, true);
        return toResponse(budget);
    }

    @Transactional
    public BudgetResponse updateBudget(UUID id, BudgetRequest request) {
        Budget budget = budgetRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Budget not found"));
        List<Integer> thresholds = normalizeThresholds(request.thresholds());
        budget.update(request.name(), request.amount(), request.currency(), request.period(),
                budget.getStartDate(), budget.getEndDate(), "ACTIVE", thresholds);
        budgetRepository.save(budget);
        evaluateAlerts(budget, false);
        return toResponse(budget);
    }

    @Transactional
    public BudgetResponse updateBudget(UUID id, String awsAccountId, BudgetRequest request) {
        validateRequest(request);
        Budget budget = findBudgetForAccount(id, awsAccountId);
        List<Integer> thresholds = normalizeThresholds(request.thresholds());
        YearMonth month = parseMonth(request.month(), YearMonth.from(budget.getStartDate()));
        budget.update(request.name(), request.amount(), request.currency(), request.period(),
            month.atDay(1), month.plusMonths(1).atDay(1).minusDays(1), "ACTIVE", thresholds);
        budgetRepository.save(budget);
        evaluateAlerts(budget, month, false);
        return toResponse(budget);
    }

    @Transactional
    public void deleteBudget(UUID id) {
        Budget budget = budgetRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Budget not found"));
        budgetAlertRepository.findByBudgetIdOrderByTriggeredAtDesc(budget.getId())
                .forEach(budgetAlertRepository::delete);
        budgetRepository.delete(budget);
    }

    @Transactional
    public void deleteBudget(UUID id, String awsAccountId) {
        Budget budget = findBudgetForAccount(id, awsAccountId);
        budgetAlertRepository.findByBudgetIdOrderByTriggeredAtDesc(budget.getId())
                .forEach(budgetAlertRepository::delete);
        budgetRepository.delete(budget);
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
        YearMonth selectedMonth = parseMonth(month, YearMonth.now());
        List<Budget> budgets = budgetRepository.findByAwsAccountId(awsAccountId);
        List<AlertResponse> alerts = new ArrayList<>();
        for (Budget budget : budgets) {
            evaluateAlerts(budget, selectedMonth, false);
            for (BudgetAlert alert : budgetAlertRepository.findByBudgetIdOrderByTriggeredAtDesc(budget.getId())) {
                alerts.add(new AlertResponse(
                        alert.getId(),
                        alert.getBudgetId(),
                        "BUDGET_THRESHOLD",
                        alert.getThresholdPercent(),
                        alert.getMessage(),
                        getActualSpend(budget.getAwsAccountId(), selectedMonth.atDay(1), selectedMonth.plusMonths(1).atDay(1)),
                        budget.getAmount(),
                        budget.getCurrency(),
                        "READ".equals(alert.getStatus()),
                        alert.getTriggeredAt()
                ));
            }
        }
        return alerts.stream().sorted((a, b) -> b.createdAt().compareTo(a.createdAt())).toList();
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
        budgetRepository.findById(alert.getBudgetId())
            .filter(budget -> awsAccountId.equals(budget.getAwsAccountId()))
            .orElseThrow(() -> new IllegalArgumentException("Alert not found"));
        alert.markRead();
        budgetAlertRepository.save(alert);
        }

    @Transactional
    public void evaluateAlerts(Budget budget, boolean createOnStartup) {
        evaluateAlerts(budget, YearMonth.from(budget.getStartDate()), createOnStartup);
    }

    public void evaluateAlerts(Budget budget, YearMonth selectedMonth, boolean createOnStartup) {
        BigDecimal actualSpend = getActualSpend(budget.getAwsAccountId(), selectedMonth.atDay(1), selectedMonth.plusMonths(1).atDay(1));
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
            boolean exists = budgetAlertRepository.existsByBudgetIdAndThresholdPercentAndAlertPeriod(budget.getId(), threshold, alertPeriod);
            if (exists) {
                continue;
            }
            String message = buildAlertMessage(threshold, budget, actualSpend);
            BudgetAlert alert = new BudgetAlert(budget.getId(), threshold, alertPeriod, "UNREAD", message);
            budgetAlertRepository.save(alert);
        }
    }

    public static String calculateStatus(BigDecimal usagePercentage) {
        if (usagePercentage.compareTo(BigDecimal.valueOf(100)) >= 0) return "EXCEEDED";
        if (usagePercentage.compareTo(BigDecimal.valueOf(90)) >= 0) return "CRITICAL";
        if (usagePercentage.compareTo(BigDecimal.valueOf(80)) >= 0) return "WARNING";
        if (usagePercentage.compareTo(BigDecimal.valueOf(50)) >= 0) return "NORMAL";
        return "SAFE";
    }

    private BudgetResponse toResponse(Budget budget) {
        return toResponse(budget, YearMonth.now());
    }

    private BudgetResponse toResponse(Budget budget, YearMonth selectedMonth) {
        LocalDate start = selectedMonth.atDay(1);
        LocalDate end = selectedMonth.plusMonths(1).atDay(1);
        BigDecimal actualSpend = getActualSpend(budget.getAwsAccountId(), start, end);
        BigDecimal usagePercentage = actualSpend == null ? null : budget.getAmount().compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : actualSpend.multiply(BigDecimal.valueOf(100)).divide(budget.getAmount(), 4, RoundingMode.HALF_UP);
        BigDecimal remainingAmount = actualSpend == null ? null : budget.getAmount().subtract(actualSpend);
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
                budget.getUpdatedAt()
        );
    }

    private String determineStatusFromUsage(BigDecimal usagePercentage) {
        if (usagePercentage.compareTo(BigDecimal.valueOf(100)) >= 0) return "EXCEEDED";
        if (usagePercentage.compareTo(BigDecimal.valueOf(90)) >= 0) return "CRITICAL";
        if (usagePercentage.compareTo(BigDecimal.valueOf(80)) >= 0) return "WARNING";
        if (usagePercentage.compareTo(BigDecimal.valueOf(50)) >= 0) return "NORMAL";
        return "SAFE";
    }

    private BigDecimal getActualSpend(String awsAccountId, LocalDate startDate, LocalDate endDate) {
        YearMonth month = YearMonth.from(startDate);
        List<AwsBilling> rows = billingSourceResolver.rows(awsAccountId, billingSourceResolver.resolve(awsAccountId, month).dataSource(), startDate, endDate);
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        BigDecimal total = rows.stream().map(AwsBilling::getCostAmount).filter(cost -> cost != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.setScale(2, RoundingMode.HALF_UP);
    }

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
        if (thresholds == null || thresholds.isEmpty()) return List.of(50, 80, 90, 100);
        if (thresholds.stream().anyMatch(v -> v == null || v <= 0 || v > 100)) {
            throw new IllegalArgumentException("Alert thresholds must be between 1 and 100.");
        }
        return thresholds.stream().distinct().sorted().toList();
    }

    private void validateRequest(BudgetRequest request) {
        if (request.name() == null || request.name().isBlank()) throw new IllegalArgumentException("Budget name cannot be empty");
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("Budget amount must be greater than 0");
        if (request.awsAccountId() == null || request.awsAccountId().isBlank()) throw new IllegalArgumentException("Valid AWS account must be selected");
        if (!"MONTHLY".equalsIgnoreCase(request.period())) throw new IllegalArgumentException("Only MONTHLY budgets are supported.");
        parseMonth(request.month(), YearMonth.now());
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
