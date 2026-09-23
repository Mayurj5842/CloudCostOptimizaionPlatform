ALTER TABLE aws_recommendations
    ADD COLUMN IF NOT EXISTS billing_month VARCHAR(7);

CREATE INDEX IF NOT EXISTS ix_aws_recommendations_account_month
    ON aws_recommendations (aws_account_id, billing_month);
