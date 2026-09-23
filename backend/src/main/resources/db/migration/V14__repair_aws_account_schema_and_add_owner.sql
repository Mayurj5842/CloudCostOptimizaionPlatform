-- V1 stored AWS credentials and the STS user id in aws_accounts. Credentials
-- belong only in the short-lived connection session, and ownership belongs to
-- the authenticated CloudCost user.
ALTER TABLE aws_accounts
    DROP COLUMN IF EXISTS access_key,
    DROP COLUMN IF EXISTS secret_key;

ALTER TABLE aws_accounts
    ADD COLUMN IF NOT EXISTS app_user_id UUID;

ALTER TABLE aws_accounts
    ADD COLUMN IF NOT EXISTS aws_user_id VARCHAR(255);

ALTER TABLE aws_accounts
    ALTER COLUMN user_id DROP NOT NULL;

UPDATE aws_accounts
SET aws_user_id = user_id
WHERE aws_user_id IS NULL;

UPDATE aws_accounts account
SET app_user_id = app_user.id
FROM app_users app_user
WHERE account.app_user_id IS NULL
  AND account.user_id = app_user.id::text;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_aws_accounts_app_user'
    ) THEN
        ALTER TABLE aws_accounts
            ADD CONSTRAINT fk_aws_accounts_app_user
            FOREIGN KEY (app_user_id) REFERENCES app_users(id) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_aws_accounts_app_user ON aws_accounts(app_user_id);