-- TASK-BE-248 Phase 1: propagate tenant_id into security-service tables.
--
-- Adds tenant_id (VARCHAR(32) NOT NULL) to login_history, suspicious_events,
-- and account_lock_history. Two-step strategy per the multi-tenancy spec:
--   1) ADD COLUMN tenant_id VARCHAR(32) NULL
--   2) backfill 'fan-platform' for any pre-existing rows (multi-tenant trait
--      was introduced after the initial GAP cut, so legacy data belongs to the
--      single B2C tenant 'fan-platform' per specs/features/multi-tenancy.md)
--   3) ALTER COLUMN tenant_id SET NOT NULL
--
-- Indexes that previously led with account_id are rebuilt to lead with
-- (tenant_id, account_id, ...). Per-tenant predicates require the leading
-- column for B+tree seek; the old indexes would force a tenant-wide scan.
-- The append-only triggers on login_history are unaffected (they fire on
-- UPDATE/DELETE, not on schema changes or index drops).
--
-- This is a single migration file because all three statement groups must
-- succeed atomically — Flyway treats per-file transactions as the unit of
-- recovery, and the three target tables share the same logical change.

-- ─────────────────────────────────────────────────────────────────────────
-- login_history
-- ─────────────────────────────────────────────────────────────────────────

ALTER TABLE login_history
    ADD COLUMN tenant_id VARCHAR(32) NULL AFTER id;

UPDATE login_history
   SET tenant_id = 'fan-platform'
 WHERE tenant_id IS NULL;

ALTER TABLE login_history
    MODIFY COLUMN tenant_id VARCHAR(32) NOT NULL;

-- Rebuild account-led indexes with tenant_id leading. The legacy
-- idx_login_history_account_id and idx_login_history_account_outcome are
-- replaced; idx_login_history_event_id (UNIQUE) and
-- idx_login_history_occurred_at remain unchanged because they are not
-- tenant-scoped.
DROP INDEX idx_login_history_account_id ON login_history;
DROP INDEX idx_login_history_account_outcome ON login_history;
CREATE INDEX idx_login_history_tenant_account
    ON login_history (tenant_id, account_id);
CREATE INDEX idx_login_history_tenant_account_outcome
    ON login_history (tenant_id, account_id, outcome);

-- ─────────────────────────────────────────────────────────────────────────
-- suspicious_events
-- ─────────────────────────────────────────────────────────────────────────

ALTER TABLE suspicious_events
    ADD COLUMN tenant_id VARCHAR(32) NULL AFTER id;

UPDATE suspicious_events
   SET tenant_id = 'fan-platform'
 WHERE tenant_id IS NULL;

ALTER TABLE suspicious_events
    MODIFY COLUMN tenant_id VARCHAR(32) NOT NULL;

DROP INDEX idx_suspicious_account_detected ON suspicious_events;
CREATE INDEX idx_suspicious_tenant_account_detected
    ON suspicious_events (tenant_id, account_id, detected_at);

-- ─────────────────────────────────────────────────────────────────────────
-- account_lock_history
-- ─────────────────────────────────────────────────────────────────────────

ALTER TABLE account_lock_history
    ADD COLUMN tenant_id VARCHAR(32) NULL AFTER id;

UPDATE account_lock_history
   SET tenant_id = 'fan-platform'
 WHERE tenant_id IS NULL;

ALTER TABLE account_lock_history
    MODIFY COLUMN tenant_id VARCHAR(32) NOT NULL;

DROP INDEX idx_account_lock_history_account_occurred ON account_lock_history;
CREATE INDEX idx_account_lock_history_tenant_account_occurred
    ON account_lock_history (tenant_id, account_id, occurred_at DESC);
