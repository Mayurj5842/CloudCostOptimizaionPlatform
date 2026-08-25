ALTER TABLE aws_billing ADD COLUMN product_name VARCHAR(255);
ALTER TABLE aws_billing ADD COLUMN usage_type VARCHAR(255);
ALTER TABLE aws_billing ADD COLUMN resource_id VARCHAR(500);
ALTER TABLE aws_billing ADD COLUMN line_item_type VARCHAR(100);
ALTER TABLE aws_billing ADD COLUMN usage_start_date DATE;
ALTER TABLE aws_billing ADD COLUMN usage_end_date DATE;
ALTER TABLE aws_billing ADD COLUMN record_key VARCHAR(128);

UPDATE aws_billing
SET usage_start_date = billing_period_start,
    usage_end_date = billing_period_end,
    record_key = md5(concat_ws('|', aws_account_id, billing_period_start, billing_period_end, service_name, coalesce(region, ''), id::text))
WHERE record_key IS NULL;

ALTER TABLE aws_billing ALTER COLUMN record_key SET NOT NULL;
ALTER TABLE aws_billing DROP CONSTRAINT IF EXISTS aws_billing_aws_account_id_billing_period_start_billing_period_end_service_name_region_key;
CREATE UNIQUE INDEX ux_aws_billing_record_key ON aws_billing(record_key);