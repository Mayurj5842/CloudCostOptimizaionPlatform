-- Both resource_id columns must always store a value so they are never NULL.
-- Rows whose source data has no resource identifier (Cost Explorer aggregates, Tax, Data Transfer,
-- service-level recommendations, ...) previously stored NULL. Backfill them with the 'N/A' placeholder
-- and enforce the column so it can never become NULL again.
-- The placeholder is never treated as a real resource id by the application.

UPDATE aws_billing SET resource_id = 'N/A' WHERE resource_id IS NULL OR btrim(resource_id) = '';
UPDATE aws_recommendations SET resource_id = 'N/A' WHERE resource_id IS NULL OR btrim(resource_id) = '';

ALTER TABLE aws_billing ALTER COLUMN resource_id SET NOT NULL;
ALTER TABLE aws_recommendations ALTER COLUMN resource_id SET NOT NULL;
