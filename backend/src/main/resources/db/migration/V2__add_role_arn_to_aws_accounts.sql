-- Add nullable role_arn column to aws_accounts to match entity
ALTER TABLE aws_accounts
ADD COLUMN role_arn VARCHAR(512);