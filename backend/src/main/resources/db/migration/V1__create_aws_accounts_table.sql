CREATE TABLE aws_accounts (
    id UUID PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    account_alias VARCHAR(255) NOT NULL,
    region VARCHAR(100) NOT NULL,
    access_key VARCHAR(255) NOT NULL,
    secret_key VARCHAR(255) NOT NULL,
    account_id VARCHAR(100) NOT NULL,
    iam_arn VARCHAR(500) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    connection_status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);