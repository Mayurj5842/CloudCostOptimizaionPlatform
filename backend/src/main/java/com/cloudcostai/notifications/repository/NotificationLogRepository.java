package com.cloudcostai.notifications.repository;

import com.cloudcostai.notifications.model.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {
    Optional<NotificationLog> findByEventKey(String eventKey);
    List<NotificationLog> findTop50ByUserIdAndAwsAccountIdOrderByCreatedAtDesc(UUID userId, String awsAccountId);
        List<NotificationLog> findTop50ByUserIdAndAwsAccountIdAndNotificationTypeNotOrderByCreatedAtDesc(
            UUID userId, String awsAccountId, String excludedType);
        long countByUserIdAndAwsAccountIdAndNotificationTypeNotAndReadAtIsNull(
            UUID userId, String awsAccountId, String excludedType);
        Optional<NotificationLog> findByIdAndUserIdAndAwsAccountIdAndNotificationTypeNot(
            UUID id, UUID userId, String awsAccountId, String excludedType);

        @Modifying
        @Query("update NotificationLog log set log.readAt = CURRENT_TIMESTAMP " +
            "where log.userId = :userId and log.awsAccountId = :accountId " +
            "and log.notificationType <> :excludedType and log.readAt is null")
        int markAllRead(@Param("userId") UUID userId, @Param("accountId") String accountId,
                @Param("excludedType") String excludedType);
}