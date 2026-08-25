CREATE TABLE aws_recommendations (
    id UUID PRIMARY KEY,
    aws_account_id VARCHAR(100) NOT NULL,
    recommendation_key VARCHAR(500) NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    category VARCHAR(100) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    service VARCHAR(255) NOT NULL,
    resource_id VARCHAR(500),
    region VARCHAR(100),
    current_cost NUMERIC(28,6) NOT NULL,
    reason TEXT NOT NULL,
    recommendation TEXT NOT NULL,
    action TEXT NOT NULL,
    potential_savings NUMERIC(28,6),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX ix_aws_recommendations_account ON aws_recommendations(aws_account_id);