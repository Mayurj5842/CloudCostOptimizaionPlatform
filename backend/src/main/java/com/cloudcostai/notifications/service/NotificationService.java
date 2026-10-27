package com.cloudcostai.notifications.service;

import com.cloudcostai.auth.repository.AppUserRepository;
import com.cloudcostai.aws.model.AwsBilling;
import com.cloudcostai.aws.repository.AwsAccountRepository;
import com.cloudcostai.aws.service.AwsBillingSourceResolver;
import com.cloudcostai.aws.service.AwsCostAnalyticsService;
import com.cloudcostai.billing.model.Budget;
import com.cloudcostai.billing.model.BudgetAlert;
import com.cloudcostai.billing.repository.BudgetRepository;
import com.cloudcostai.notifications.dto.NotificationHistoryResponse;
import com.cloudcostai.notifications.dto.NotificationFeedResponse;
import com.cloudcostai.notifications.dto.NotificationPreferenceRequest;
import com.cloudcostai.notifications.dto.NotificationPreferenceResponse;
import com.cloudcostai.notifications.model.NotificationLog;
import com.cloudcostai.notifications.model.NotificationPreference;
import com.cloudcostai.notifications.repository.NotificationLogRepository;
import com.cloudcostai.notifications.repository.NotificationPreferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class NotificationService {
    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    private final AppUserRepository userRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationLogRepository logRepository;
    private final EmailService emailService;
    private final AwsBillingSourceResolver billingSourceResolver;
    private final BudgetRepository budgetRepository;
    private final AwsCostAnalyticsService analyticsService;
    private final AwsAccountRepository awsAccountRepository;

    public NotificationService(AppUserRepository userRepository,
                               NotificationPreferenceRepository preferenceRepository,
                               NotificationLogRepository logRepository,
                               EmailService emailService,
                               AwsBillingSourceResolver billingSourceResolver,
                               BudgetRepository budgetRepository,
                               AwsCostAnalyticsService analyticsService,
                               AwsAccountRepository awsAccountRepository) {
        this.userRepository = userRepository;
        this.preferenceRepository = preferenceRepository;
        this.logRepository = logRepository;
        this.emailService = emailService;
        this.billingSourceResolver = billingSourceResolver;
        this.budgetRepository = budgetRepository;
        this.analyticsService = analyticsService;
        this.awsAccountRepository = awsAccountRepository;
    }

    @Transactional
    public NotificationPreferenceResponse getPreferences(UUID userId, String accountId) {
        NotificationPreference preference = preferenceRepository.findByUserIdAndAwsAccountId(userId, normalizeAccountId(accountId))
                .orElseGet(() -> {
                    String email = userRepository.findById(userId)
                            .orElseThrow(() -> new IllegalArgumentException("Authenticated user was not found."))
                            .profile().email();
                    return preferenceRepository.save(new NotificationPreference(userId, accountId.trim(), email));
                });
        return NotificationPreferenceResponse.from(preference);
    }

    @Transactional
    public NotificationPreferenceResponse savePreferences(UUID userId, NotificationPreferenceRequest request) {
        String accountId = normalizeAccountId(request.awsAccountId());
        if (!List.of(80, 90, 100).contains(request.budgetAlertThreshold())) {
            throw new IllegalArgumentException("Budget threshold must be 80%, 90%, or 100%.");
        }
        NotificationPreference preference = preferenceRepository.findByUserIdAndAwsAccountId(userId, accountId)
                .orElseGet(() -> new NotificationPreference(userId, accountId, request.emailAddress().trim()));
        preference.update(request.emailAddress().trim(), request.emailNotificationsEnabled(), request.budgetAlertEnabled(),
                request.budgetAlertThreshold(), request.budgetExceededEnabled(), request.costAnomalyEnabled(),
                request.monthlySummaryEnabled());
        return NotificationPreferenceResponse.from(preferenceRepository.save(preference));
    }

    public String sendTest(UUID userId, String accountId) {
        NotificationPreference preference = preference(userId, accountId);
        String eventKey = "TEST:" + userId + ":" + preference.getAwsAccountId() + ":" + UUID.randomUUID();
        String subject = "CloudCostAI test email";
        NotificationLog log = logRepository.saveAndFlush(new NotificationLog(userId, preference.getAwsAccountId(),
            "TEST", eventKey, preference.getEmailAddress(), subject,
            "This message confirms that CloudCostAI can deliver email using the configured SMTP service."));
        try {
            emailService.send(preference.getEmailAddress(), subject, "Test Email", preference.getAwsAccountId(),
                    "Not applicable", "Not available", "Not available", "Not available",
                    "This message confirms that CloudCostAI can deliver email using the configured SMTP service.");
            log.markSent();
            logRepository.save(log);
            return "Test email sent to " + preference.getEmailAddress() + ".";
        } catch (RuntimeException exception) {
            log.markFailed(safeError(exception));
            logRepository.save(log);
            throw exception;
        }
    }

    public List<NotificationHistoryResponse> history(UUID userId, String accountId) {
        return logRepository.findTop50ByUserIdAndAwsAccountIdOrderByCreatedAtDesc(userId, normalizeAccountId(accountId))
                .stream().map(NotificationHistoryResponse::from).toList();
    }

    @Transactional
    public void sendBudgetAlert(BudgetAlert alert) {
        if (alert.getAwsAccountId() == null || alert.getEventKey() == null) return;
        for (NotificationPreference preference : preferenceRepository.findByEmailNotificationsEnabledTrue()) {
            if (!preference.isEmailNotificationsEnabled()
                    || !alert.getAwsAccountId().equals(preference.getAwsAccountId())
                    || !allowsBudgetAlert(preference, alert)) {
                continue;
            }
            BigDecimal usage = percentage(alert.getActualSpend(), alert.getBudgetAmount());
            sendEvent(preference, alert.getAlertType(), "BUDGET_ALERT:" + alert.getEventKey(), alert.getTitle(),
                    alert.getAwsAccountId(), alert.getAlertPeriod(), alert.getActualSpend(), alert.getBudgetAmount(),
                    usage, alert.getCurrency() == null ? "USD" : alert.getCurrency(), alert.getMessage());
        }
    }

    private boolean allowsBudgetAlert(NotificationPreference preference, BudgetAlert alert) {
        return switch (alert.getAlertType()) {
            case "BUDGET_THRESHOLD" -> preference.isBudgetAlertEnabled()
                    && alert.getThresholdPercent() >= preference.getBudgetAlertThreshold();
            case "BUDGET_EXCEEDED" -> preference.isBudgetExceededEnabled();
            case "SPENDING_SPIKE" -> preference.isCostAnomalyEnabled();
            default -> false;
        };
    }

    @Transactional(readOnly = true)
    public NotificationFeedResponse recent(UUID userId, String accountId) {
        String normalizedAccountId = normalizeAccountId(accountId);
        return NotificationFeedResponse.from(
                logRepository.findTop50ByUserIdAndAwsAccountIdAndNotificationTypeNotOrderByCreatedAtDesc(
                        userId, normalizedAccountId, "TEST"),
                logRepository.countByUserIdAndAwsAccountIdAndNotificationTypeNotAndReadAtIsNull(
                        userId, normalizedAccountId, "TEST"));
    }

    @Transactional
    public void markRead(UUID userId, String accountId, UUID notificationId) {
        String normalizedAccountId = normalizeAccountId(accountId);
        NotificationLog log = logRepository.findByIdAndUserIdAndAwsAccountIdAndNotificationTypeNot(
                        notificationId, userId, normalizedAccountId, "TEST")
                .orElseThrow(() -> new IllegalArgumentException("Notification was not found."));
        log.markRead();
        logRepository.save(log);
    }

    @Transactional
    public int markAllRead(UUID userId, String accountId) {
        return logRepository.markAllRead(userId, normalizeAccountId(accountId), "TEST");
    }

    public void evaluateEnabledPreferences() {
        for (NotificationPreference preference : preferenceRepository.findByEmailNotificationsEnabledTrue()) {
            try {
                evaluate(preference);
            } catch (RuntimeException exception) {
                logger.warn("Notification evaluation failed for account={}: {}", preference.getAwsAccountId(), safeError(exception));
            }
        }
    }

    private void evaluate(NotificationPreference preference) {
        YearMonth currentMonth = YearMonth.now();
        BillingSnapshot current = snapshot(preference.getAwsAccountId(), currentMonth);
        if (current == null) return;

        Budget budget = activeBudget(preference.getAwsAccountId());
        BigDecimal limit = budget == null ? null : budget.getAmount();
        BigDecimal usage = percentage(current.total(), limit);
        String period = period(currentMonth);
        String account = accountLabel(preference.getAwsAccountId());

        if (budget != null && usage != null) {
            if (preference.isBudgetAlertEnabled() && usage.compareTo(BigDecimal.valueOf(preference.getBudgetAlertThreshold())) >= 0) {
                sendEvent(preference, "BUDGET_THRESHOLD", "BUDGET_THRESHOLD:" + currentMonth + ":" + preference.getBudgetAlertThreshold(),
                        "Budget threshold reached: " + preference.getBudgetAlertThreshold() + "%", account, period,
                    current.total(), limit, usage, current.currency(), "Current spend has reached the configured budget threshold.");
            }
            if (preference.isBudgetExceededEnabled() && usage.compareTo(BigDecimal.valueOf(100)) >= 0) {
                sendEvent(preference, "BUDGET_EXCEEDED", "BUDGET_EXCEEDED:" + currentMonth,
                    "AWS budget exceeded", account, period, current.total(), limit, usage, current.currency(),
                        "Current spend has reached or exceeded the configured budget.");
            }
        }

        if (preference.isCostAnomalyEnabled()) {
            try {
                var analytics = analyticsService.getAnalyticsForAccount(preference.getAwsAccountId(), "CURRENT_MONTH", currentMonth.toString());
                if (analytics.anomalies() != null) {
                    for (var anomaly : analytics.anomalies()) {
                        sendEvent(preference, "COST_ANOMALY", "COST_ANOMALY:" + currentMonth + ":" + anomaly.date(),
                                "Unusual AWS cost increase detected", account, period, anomaly.cost(), limit, usage,
                                analytics.currency(), anomaly.percentageAboveAverage() + "% above the existing cost-analysis baseline on " + anomaly.date() + ".");
                    }
                }
            } catch (RuntimeException exception) {
                logger.debug("Cost anomaly check skipped for account={}: {}", preference.getAwsAccountId(), safeError(exception));
            }
        }

        if (preference.isMonthlySummaryEnabled()) {
            sendMonthlySummary(preference, currentMonth.minusMonths(1), account);
        }
    }

    private void sendMonthlySummary(NotificationPreference preference, YearMonth month, String account) {
        BillingSnapshot summary = snapshot(preference.getAwsAccountId(), month);
        if (summary == null) return;
        Budget budget = activeBudget(preference.getAwsAccountId());
        BigDecimal limit = budget == null ? null : budget.getAmount();
        BigDecimal usage = percentage(summary.total(), limit);
        String topServices = summary.rows().stream()
                .filter(row -> row.getCostAmount() != null)
                .collect(Collectors.groupingBy(row -> row.getServiceName() == null ? "Other AWS services" : row.getServiceName(),
                        Collectors.reducing(BigDecimal.ZERO, AwsBilling::getCostAmount, BigDecimal::add)))
                .entrySet().stream().sorted((left, right) -> right.getValue().compareTo(left.getValue())).limit(5)
                .map(entry -> entry.getKey() + ": " + money(entry.getValue(), summary.currency()))
                .collect(Collectors.joining("\n"));
        sendEvent(preference, "MONTHLY_SUMMARY", "MONTHLY_SUMMARY:" + month,
                "CloudCostAI monthly cost summary: " + month, account, period(month), summary.total(), limit, usage,
            summary.currency(), "Top AWS services by cost:\n" + (topServices.isBlank() ? "No service cost data" : topServices));
    }

    private BillingSnapshot snapshot(String accountId, YearMonth month) {
        var resolved = billingSourceResolver.resolve(accountId, month);
        List<AwsBilling> rows = resolved.rows();
        if (rows.isEmpty()) return null;
        BigDecimal total = rows.stream().map(AwsBilling::getCostAmount).filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String currency = rows.stream().map(AwsBilling::getCurrency).filter(value -> value != null && !value.isBlank())
                .findFirst().orElse("USD");
        return new BillingSnapshot(rows, total, currency);
    }

    private Budget activeBudget(String accountId) {
        return budgetRepository.findByAwsAccountIdOrderByCreatedAtDesc(accountId).stream()
                .filter(budget -> "ACTIVE".equals(budget.getStatus())).findFirst().orElse(null);
    }

    private void sendEvent(NotificationPreference preference, String type, String key, String subject,
                           String account, String period, BigDecimal cost, BigDecimal budget,
                           BigDecimal usage, String currency, String details) {
        String eventKey = preference.getUserId() + ":" + preference.getAwsAccountId() + ":" + key;
        NotificationLog log = logRepository.findByEventKey(eventKey).orElseGet(() -> logRepository.saveAndFlush(
                new NotificationLog(preference.getUserId(), preference.getAwsAccountId(), type, eventKey,
                preference.getEmailAddress(), subject, details)));
        if ("SENT".equals(log.getStatus())) return;
        try {
            emailService.send(preference.getEmailAddress(), subject, type, account, period,
                    cost == null ? "Not available" : money(cost, currency),
                    budget == null ? "Not configured" : money(budget, currency),
                    usage == null ? "Not available" : usage.stripTrailingZeros().toPlainString() + "%", details);
            log.markSent();
        } catch (RuntimeException exception) {
            log.markFailed(safeError(exception));
            logger.warn("Email notification failed type={} account={}: {}", type, preference.getAwsAccountId(), safeError(exception));
        }
        logRepository.save(log);
    }

    private NotificationPreference preference(UUID userId, String accountId) {
        return preferenceRepository.findByUserIdAndAwsAccountId(userId, normalizeAccountId(accountId))
                .orElseThrow(() -> new IllegalArgumentException("Save notification preferences before sending a test email."));
    }

    private String accountLabel(String accountId) {
        return awsAccountRepository.findByAccountIdAndProviderAndConnectionStatus(accountId, "AWS", "CONNECTED")
                .map(account -> account.getAccountAlias() + " (" + accountId + ")").orElse(accountId);
    }

    private String normalizeAccountId(String accountId) {
        if (accountId == null || accountId.isBlank()) throw new IllegalArgumentException("AWS account ID is required.");
        return accountId.trim();
    }

    private BigDecimal percentage(BigDecimal cost, BigDecimal budget) {
        if (budget == null || budget.signum() <= 0) return null;
        return cost.multiply(BigDecimal.valueOf(100)).divide(budget, 2, RoundingMode.HALF_UP);
    }

    private String money(BigDecimal amount, String currency) {
        return currency + " " + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String period(YearMonth month) {
        return month.atDay(1) + " to " + month.atEndOfMonth();
    }

    private String safeError(RuntimeException exception) {
        String message = exception.getMessage() == null ? "Email operation failed." : exception.getMessage();
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private record BillingSnapshot(List<AwsBilling> rows, BigDecimal total, String currency) {}
}