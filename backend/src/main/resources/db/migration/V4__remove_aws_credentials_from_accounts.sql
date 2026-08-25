ALTER TABLE aws_accounts
    DROP COLUMN IF EXISTS access_key,
    DROP COLUMN IF EXISTS secret_key;