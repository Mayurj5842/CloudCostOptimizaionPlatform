ALTER TABLE aws_billing
    ADD COLUMN IF NOT EXISTS data_source VARCHAR(32);

UPDATE aws_billing
SET data_source = 'CUR_2_0'
WHERE data_source IS NULL;

ALTER TABLE aws_billing
    ALTER COLUMN data_source SET NOT NULL;

CREATE INDEX IF NOT EXISTS ix_aws_billing_account_source_period
    ON aws_billing (aws_account_id, data_source, billing_period_start, billing_period_end);
