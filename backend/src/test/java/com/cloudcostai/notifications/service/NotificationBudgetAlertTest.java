package com.cloudcostai.notifications.service;

import com.cloudcostai.auth.repository.AppUserRepository;
import com.cloudcostai.aws.repository.AwsAccountRepository;
import com.cloudcostai.aws.service.AwsBillingSourceResolver;
import com.cloudcostai.aws.service.AwsCostAnalyticsService;
import com.cloudcostai.billing.model.BudgetAlert;
import com.cloudcostai.billing.repository.BudgetRepository;
import com.cloudcostai.notifications.model.NotificationLog;
import com.cloudcostai.notifications.model.NotificationPreference;
import com.cloudcostai.notifications.repository.NotificationLogRepository;
import com.cloudcostai.notifications.repository.NotificationPreferenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationBudgetAlertTest {
    @Mock private AppUserRepository userRepository;
    @Mock private NotificationPreferenceRepository preferenceRepository;
    @Mock private NotificationLogRepository logRepository;
    @Mock private EmailService emailService;
    @Mock private AwsBillingSourceResolver billingSourceResolver;
    @Mock private BudgetRepository budgetRepository;
    @Mock private AwsCostAnalyticsService analyticsService;
    @Mock private AwsAccountRepository awsAccountRepository;

    @Test
    void deliversBudgetAlertOnceThroughExistingPreferenceAndEmailLogFlow() {
        UUID userId = UUID.randomUUID();
        String accountId = "123456789012";
        String email = "finops@example.com";
        String eventKey = userId + ":" + accountId + ":BUDGET_ALERT:budget-event";
        NotificationPreference preference = preference(userId, accountId, email, true, true, 80);
        NotificationLog sentLog = new NotificationLog(userId, accountId, "BUDGET_THRESHOLD", eventKey,
                email, "75% threshold reached", "Budget reached configured threshold.");
        sentLog.markSent();
        when(preferenceRepository.findByEmailNotificationsEnabledTrue()).thenReturn(List.of(preference));
        when(logRepository.findByEventKey(eventKey)).thenReturn(Optional.empty(), Optional.of(sentLog));
        when(logRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationService service = service();
        service.sendBudgetAlert(alert(accountId, 90, "BUDGET_THRESHOLD", "budget-event"));
        service.sendBudgetAlert(alert(accountId, 90, "BUDGET_THRESHOLD", "budget-event"));

        verify(emailService, times(1)).send(eq(email), eq("Budget threshold reached"), eq("BUDGET_THRESHOLD"),
                eq(accountId), eq("2026-09"), eq("USD 90.00"), eq("USD 100.00"), eq("90%"),
                eq("Budget reached configured threshold."));
    }

    @Test
    void respectsDisabledEmailAndBudgetThresholdPreferences() {
        UUID userId = UUID.randomUUID();
        String accountId = "123456789012";
        NotificationPreference preference = preference(userId, accountId, "finops@example.com", true, false, 80);
        when(preferenceRepository.findByEmailNotificationsEnabledTrue()).thenReturn(List.of(preference));

        service().sendBudgetAlert(alert(accountId, 90, "BUDGET_THRESHOLD", "budget-event"));

        verify(logRepository, never()).findByEventKey(anyString());
        verify(emailService, never()).send(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void respectsTheGlobalEmailNotificationToggle() {
        UUID userId = UUID.randomUUID();
        String accountId = "123456789012";
        NotificationPreference preference = preference(userId, accountId, "finops@example.com", false, true, 80);
        when(preferenceRepository.findByEmailNotificationsEnabledTrue()).thenReturn(List.of(preference));

        service().sendBudgetAlert(alert(accountId, 90, "BUDGET_THRESHOLD", "budget-event"));

        verify(logRepository, never()).findByEventKey(anyString());
        verify(emailService, never()).send(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString());
    }

    private NotificationService service() {
        return new NotificationService(userRepository, preferenceRepository, logRepository, emailService,
                billingSourceResolver, budgetRepository, analyticsService, awsAccountRepository);
    }

    private NotificationPreference preference(UUID userId, String accountId, String email,
                                              boolean emailEnabled, boolean budgetEnabled, int threshold) {
        NotificationPreference preference = new NotificationPreference(userId, accountId, email);
        preference.update(email, emailEnabled, budgetEnabled, threshold, true, true, true);
        return preference;
    }

    private BudgetAlert alert(String accountId, int threshold, String type, String eventKey) {
        return new BudgetAlert(UUID.randomUUID(), threshold, "2026-09", "UNREAD",
                "Budget reached configured threshold.", type, "WARNING", "Budget threshold reached", "Production",
                accountId, "Amazon EC2", new BigDecimal("90.00"), new BigDecimal("100.00"), "USD", null, null,
                eventKey);
    }
}