package com.cloudcostai.notifications.repository;

import com.cloudcostai.notifications.model.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {
    Optional<NotificationPreference> findByUserIdAndAwsAccountId(UUID userId, String awsAccountId);
    List<NotificationPreference> findByEmailNotificationsEnabledTrue();
}