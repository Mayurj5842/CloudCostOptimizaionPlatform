package com.cloudcostai.notifications.dto;

import com.cloudcostai.notifications.model.NotificationLog;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationHistoryResponse(
        UUID id,
        String awsAccountId,
        String notificationType,
        String recipient,
        String subject,
        String status,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime sentAt
) {
    public static NotificationHistoryResponse from(NotificationLog log) {
        return new NotificationHistoryResponse(log.getId(), log.getAwsAccountId(), log.getNotificationType(),
                log.getRecipient(), log.getSubject(), log.getStatus(), log.getErrorMessage(),
                log.getCreatedAt(), log.getSentAt());
    }
}