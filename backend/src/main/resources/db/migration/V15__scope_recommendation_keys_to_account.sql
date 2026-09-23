ALTER TABLE aws_recommendations
    DROP CONSTRAINT IF EXISTS aws_recommendations_recommendation_key_key;

ALTER TABLE aws_recommendations
    ADD CONSTRAINT uq_aws_recommendations_account_key UNIQUE (aws_account_id, recommendation_key);
