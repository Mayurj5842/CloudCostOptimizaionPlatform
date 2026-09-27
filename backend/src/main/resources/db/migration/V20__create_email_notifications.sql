CREATE TABLE notification_preferences (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    aws_account_id VARCHAR(100) NOT NULL,
    email_address VARCHAR(320) NOT NULL,
    email_notifications_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    budget_alert_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    budget_alert_threshold INTEGER NOT NULL DEFAULT 80,
    budget_exceeded_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    cost_anomaly_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    monthly_summary_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_notification_preferences_user_account UNIQUE (user_id, aws_account_id),
    CONSTRAINT ck_notification_threshold CHECK (budget_alert_threshold IN (80, 90, 100))
);

CREATE INDEX idx_notification_preferences_enabled
    ON notification_preferences(email_notifications_enabled);

CREATE TABLE notification_logs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    aws_account_id VARCHAR(100) NOT NULL,
    notification_type VARCHAR(40) NOT NULL,
    event_key VARCHAR(300) NOT NULL UNIQUE,
    recipient VARCHAR(320) NOT NULL,
    subject VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL,
    sent_at TIMESTAMP
);

CREATE INDEX idx_notification_logs_user_account_created
    ON notification_logs(user_id, aws_account_id, created_at DESC);