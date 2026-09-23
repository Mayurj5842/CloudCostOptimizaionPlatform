ALTER TABLE aws_billing_summary ADD COLUMN source_delivery_id VARCHAR(1024);
ALTER TABLE aws_billing_summary ADD COLUMN source_delivery_at TIMESTAMP WITH TIME ZONE;
