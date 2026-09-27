package com.cloudcostai.notifications.dto;

import com.cloudcostai.notifications.model.NotificationPreference;

public record NotificationPreferenceResponse(
        String awsAccountId,
        String emailAddress,
        boolean emailNotificationsEnabled,
        boolean budgetAlertEnabled,
        int budgetAlertThreshold,
        boolean budgetExceededEnabled,
        boolean costAnomalyEnabled,
        boolean monthlySummaryEnabled
) {
    public static NotificationPreferenceResponse from(NotificationPreference preference) {
        return new NotificationPreferenceResponse(preference.getAwsAccountId(), preference.getEmailAddress(),
                preference.isEmailNotificationsEnabled(), preference.isBudgetAlertEnabled(),
                preference.getBudgetAlertThreshold(), preference.isBudgetExceededEnabled(),
                preference.isCostAnomalyEnabled(), preference.isMonthlySummaryEnabled());
    }
}