ALTER TABLE notification_logs
    ADD COLUMN notification_message TEXT NOT NULL DEFAULT '',
    ADD COLUMN read_at TIMESTAMP;