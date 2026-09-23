ALTER TABLE aws_recommendations ADD COLUMN billing_month VARCHAR(7);
ALTER TABLE aws_recommendations ADD COLUMN evidence TEXT;
UPDATE aws_recommendations SET billing_month = to_char(created_at, 'YYYY-MM') WHERE billing_month IS NULL;
ALTER TABLE aws_recommendations ALTER COLUMN billing_month SET NOT NULL;
CREATE INDEX ix_aws_recommendations_account_month ON aws_recommendations(aws_account_id, billing_month);