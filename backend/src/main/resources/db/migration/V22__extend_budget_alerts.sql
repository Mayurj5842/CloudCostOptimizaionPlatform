ALTER TABLE budgets
    ADD COLUMN service_name VARCHAR(255),
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN spike_threshold_percent INTEGER NOT NULL DEFAULT 50;

ALTER TABLE budget_alerts
    ADD COLUMN alert_type VARCHAR(40) NOT NULL DEFAULT 'BUDGET_THRESHOLD',
    ADD COLUMN severity VARCHAR(20) NOT NULL DEFAULT 'WARNING',
    ADD COLUMN title VARCHAR(255) NOT NULL DEFAULT 'Budget threshold reached',
    ADD COLUMN budget_name VARCHAR(255),
    ADD COLUMN aws_account_id VARCHAR(100),
    ADD COLUMN service_name VARCHAR(255),
    ADD COLUMN actual_spend NUMERIC(19, 4),
    ADD COLUMN budget_amount NUMERIC(19, 4),
    ADD COLUMN currency VARCHAR(10),
    ADD COLUMN comparison_period VARCHAR(50),
    ADD COLUMN percentage_change NUMERIC(9, 2),
    ADD COLUMN event_key VARCHAR(300);

UPDATE budget_alerts alert
SET budget_name = budget.name,
    aws_account_id = budget.aws_account_id,
    service_name = budget.service_name,
    budget_amount = budget.amount,
    currency = budget.currency,
    title = CASE WHEN alert.threshold_percent >= 100 THEN 'Budget exceeded' ELSE 'Budget threshold reached' END,
    severity = CASE WHEN alert.threshold_percent >= 100 THEN 'CRITICAL'
                    WHEN alert.threshold_percent >= 75 THEN 'WARNING'
                    ELSE 'INFO' END
FROM budgets budget
WHERE alert.budget_id = budget.id;

ALTER TABLE budget_alerts ALTER COLUMN budget_id DROP NOT NULL;
ALTER TABLE budget_alerts DROP CONSTRAINT IF EXISTS budget_alerts_budget_id_fkey;
ALTER TABLE budget_alerts ADD CONSTRAINT budget_alerts_budget_id_fkey
    FOREIGN KEY (budget_id) REFERENCES budgets(id) ON DELETE SET NULL;

CREATE UNIQUE INDEX uq_budget_alert_event_key ON budget_alerts(event_key);
CREATE INDEX idx_budget_alerts_account_period ON budget_alerts(aws_account_id, alert_period, triggered_at DESC);