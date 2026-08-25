CREATE TABLE aws_billing (
    id UUID PRIMARY KEY,
    aws_account_id VARCHAR(100) NOT NULL,
    billing_period_start DATE NOT NULL,
    billing_period_end DATE NOT NULL,
    service_name VARCHAR(255) NOT NULL,
    region VARCHAR(100),
    usage_amount NUMERIC(28,6) NOT NULL,
    cost_amount NUMERIC(28,6) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    estimated BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE (aws_account_id, billing_period_start, billing_period_end, service_name, region)
);

CREATE TABLE aws_billing_summary (
    id UUID PRIMARY KEY,
    aws_account_id VARCHAR(100) NOT NULL,
    billing_period VARCHAR(50) NOT NULL,
    total_cost NUMERIC(28,6) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    estimated BOOLEAN NOT NULL,
    last_synced_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE (aws_account_id, billing_period)
);
