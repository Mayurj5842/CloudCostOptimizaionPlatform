package com.cloudcostai.notifications.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification_logs")
public class NotificationLog {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "aws_account_id", nullable = false, length = 100)
    private String awsAccountId;

    @Column(name = "notification_type", nullable = false, length = 40)
    private String notificationType;

    @Column(name = "event_key", nullable = false, unique = true, length = 300)
    private String eventKey;

    @Column(nullable = false, length = 320)
    private String recipient;

    @Column(nullable = false, length = 500)
    private String subject;

    @Column(name = "notification_message", nullable = false)
    private String notificationMessage;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    protected NotificationLog() {}

    public NotificationLog(UUID userId, String awsAccountId, String notificationType, String eventKey,
                           String recipient, String subject, String notificationMessage) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.awsAccountId = awsAccountId;
        this.notificationType = notificationType;
        this.eventKey = eventKey;
        this.recipient = recipient;
        this.subject = subject;
        this.notificationMessage = notificationMessage;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
    }

    public void markSent() {
        this.status = "SENT";
        this.errorMessage = null;
        this.sentAt = LocalDateTime.now();
    }

    public void markFailed(String errorMessage) {
        this.status = "FAILED";
        this.errorMessage = errorMessage;
        this.sentAt = null;
    }

    public void markRead() {
        if (readAt == null) readAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getAwsAccountId() { return awsAccountId; }
    public String getNotificationType() { return notificationType; }
    public String getRecipient() { return recipient; }
    public String getSubject() { return subject; }
    public String getNotificationMessage() { return notificationMessage; }
    public String getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getSentAt() { return sentAt; }
    public LocalDateTime getReadAt() { return readAt; }
    public boolean isRead() { return readAt != null; }
}