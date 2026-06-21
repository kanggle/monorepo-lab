-- TASK-BE-425 — proportional commission clawback (partial refunds).
--
-- Links each REVERSAL row to the ACCRUAL it (partially) reverses, so per-accrual
-- cumulative reversed can be computed exactly across multiple partial refunds (the
-- final fullyRefunded refund nets each accrual to exactly zero). Nullable: ACCRUAL
-- rows and legacy REVERSAL rows (written before partial refunds) carry NULL.

ALTER TABLE commission_accrual ADD COLUMN reverses_accrual_id VARCHAR(255);

-- Supports the per-order reversal lookup that aggregates already-reversed per accrual.
CREATE INDEX idx_commission_accrual_reverses ON commission_accrual (reverses_accrual_id);
