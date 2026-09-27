package com.cloudcostai.notifications.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NotificationPreferenceRequest(
        @NotBlank String awsAccountId,
        @NotBlank @Email String emailAddress,
        @NotNull Boolean emailNotificationsEnabled,
        @NotNull Boolean budgetAlertEnabled,
        @NotNull Integer budgetAlertThreshold,
        @NotNull Boolean budgetExceededEnabled,
        @NotNull Boolean costAnomalyEnabled,
        @NotNull Boolean monthlySummaryEnabled
) {}