CREATE TABLE aws_connection_sessions (
    token VARCHAR(64) PRIMARY KEY,
    account_id VARCHAR(128) NOT NULL,
    access_key VARCHAR(512) NOT NULL,
    secret_key VARCHAR(512) NOT NULL,
    region VARCHAR(100) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    UNIQUE (account_id)
);

CREATE INDEX idx_aws_connection_session_account ON aws_connection_sessions(account_id);
