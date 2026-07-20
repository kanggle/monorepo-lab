-- TASK-BE-535 — duplicate OPEN settlement period is a double-payout vector.
--
-- OpenSettlementPeriodUseCase mints a fresh UUID per call with no client key, so every
-- replay of POST /api/admin/settlements/periods opened ANOTHER period over the same
-- window. No money moves at open time, but close() folds the in-window accruals into
-- seller_payout rows — so two OPEN periods over one accrual window, each closed, pay
-- each seller twice.
--
-- The guard is a DB constraint rather than a client Idempotency-Key because this
-- endpoint has a live console caller (platform-console → /api/ecommerce/settlements/
-- periods) that sends no key; a natural-key constraint needs no caller change and is
-- also the AC-5 concurrency backstop — two simultaneous duplicates cannot both commit,
-- because the second insert is rejected by the index, not by a read-then-write check.
--
-- PARTIAL (WHERE status = 'OPEN') on purpose. A full unique index would also block
-- re-opening the same window AFTER the earlier period was closed, which is a
-- legitimate correction re-run (recompute a month that was closed with a bad
-- commission rate). Only concurrently-open duplicates are the double-payout vector,
-- so only those are refused.
--
-- Genuine OVERLAP (non-identical windows) is deliberately NOT guarded: the
-- SettlementPeriod javadoc states a tenant may run overlapping windows, and the close
-- folds whichever accruals fall in [from, to). Narrowing that is a separate product
-- decision. This index constrains exact duplicates only.
--
-- Violation surfaces as DataIntegrityViolationException, translated by
-- OpenSettlementPeriodUseCase to PeriodAlreadyOpenException → 409 PERIOD_ALREADY_OPEN.

CREATE UNIQUE INDEX ux_settlement_period_open_window
    ON settlement_period (tenant_id, period_from, period_to)
    WHERE status = 'OPEN';
