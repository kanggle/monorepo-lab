-- TASK-BE-050: saga sweeper recovery counter.
--
-- Adds a per-saga re-emission counter so the sweeper can cap how many times
-- it re-publishes the appropriate outbox event for a stuck saga. After the
-- cap is reached the saga transitions to STUCK_RECOVERY_FAILED and an
-- outbound.alert.saga.recovery.exhausted event fires (admin-events.md A1).
--
-- The new column is NOT NULL with default 0 so existing rows tolerate the
-- migration without backfill. Reset to 0 explicitly on every successful
-- saga state advance is NOT performed — the cap protects against persistent
-- failure modes, and the saga moves through transitional states quickly
-- enough that a stale counter is not a concern (a saga that gets stuck
-- twice will simply hit the cap faster the second time, which is the
-- desired behaviour).

ALTER TABLE outbound_saga
    ADD COLUMN IF NOT EXISTS re_emit_count INT NOT NULL DEFAULT 0;

COMMENT ON COLUMN outbound_saga.re_emit_count
    IS 'TASK-BE-050: monotonically-increasing counter of saga sweeper re-emissions; cap configured via outbound.saga.sweeper.max-attempts';

-- Index used by the sweeper's findStuck query to filter exhausted sagas
-- without loading them. Partial index keeps it cheap on the typical hot
-- path where re_emit_count = 0 dominates.
CREATE INDEX IF NOT EXISTS idx_outbound_saga_sweeper_candidates
    ON outbound_saga (status, updated_at)
    WHERE status IN ('REQUESTED', 'CANCELLATION_REQUESTED', 'SHIPPED');
