-- TASK-BE-425 — partial payment refunds.
--
-- Cumulative refunded minor units on each payment. Enables partial refunds
-- (status PARTIALLY_REFUNDED until refunded_amount reaches amount).
--
-- Zero-downtime 3-step: ADD nullable -> backfill -> SET NOT NULL DEFAULT 0.
-- Backfill: pre-existing fully-REFUNDED rows had the whole amount refunded, so
-- refunded_amount = amount; every other row has refunded nothing yet (0).

ALTER TABLE payments ADD COLUMN refunded_amount BIGINT;
UPDATE payments SET refunded_amount = amount WHERE status = 'REFUNDED';
UPDATE payments SET refunded_amount = 0 WHERE refunded_amount IS NULL;
ALTER TABLE payments ALTER COLUMN refunded_amount SET DEFAULT 0;
ALTER TABLE payments ALTER COLUMN refunded_amount SET NOT NULL;
