package com.cloudcostai.notifications.dto;

import com.cloudcostai.notifications.model.NotificationLog;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record NotificationFeedResponse(List<Item> notifications, long unreadCount) {
    public static NotificationFeedResponse from(List<NotificationLog> logs, long unreadCount) {
        return new NotificationFeedResponse(logs.stream().map(Item::from).toList(), unreadCount);
    }

    public record Item(UUID id, String type, String title, String message, String severity,
                       LocalDateTime createdAt, boolean read) {
        private static Item from(NotificationLog log) {
            String message = log.getNotificationMessage();
            if (message == null || message.isBlank()) message = log.getSubject();
            return new Item(log.getId(), log.getNotificationType(), log.getSubject(),
                message, severity(log.getNotificationType()),
                    log.getCreatedAt(), log.isRead());
        }

        private static String severity(String type) {
            return switch (type) {
                case "BUDGET_EXCEEDED" -> "CRITICAL";
                case "BUDGET_THRESHOLD", "COST_ANOMALY" -> "WARNING";
                default -> "INFO";
            };
        }
    }
}