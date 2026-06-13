ALTER TABLE task ADD COLUMN last_reconciled_date DATE;

UPDATE task SET last_reconciled_date = CAST(created_at AS DATE) WHERE last_reconciled_date IS NULL;

ALTER TABLE task ALTER COLUMN last_reconciled_date SET NOT NULL;
