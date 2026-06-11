-- TASK-FAN-BE-014: membership expiry sweeper marker (Option B).
--
-- Adds a one-time `expiry_notified_at` marker so the scheduled expiry sweeper can
-- emit `fan.membership.expired.v1` EXACTLY ONCE per membership. This is NOT a
-- stored EXPIRED status — `ck_membership_status` stays ('ACTIVE','CANCELED') and
-- the `status` column is never changed by the sweep (read-time expiry remains
-- authoritative; see membership-service architecture.md § Expiry Sweeper). The
-- `membership-api.md` list/detail `status` enum is therefore unchanged.
ALTER TABLE memberships ADD COLUMN expiry_notified_at TIMESTAMPTZ;

-- Partial index matching the sweep predicate
--   status = 'ACTIVE' AND valid_to < now AND expiry_notified_at IS NULL
-- (the constant `now` is supplied per-query; the index covers the two static
-- predicates + orders by valid_to). The index shrinks toward empty as past-window
-- ACTIVE rows are swept, so the sweep scan stays cheap regardless of table size.
CREATE INDEX idx_memberships_expiry_sweep
    ON memberships (valid_to)
    WHERE status = 'ACTIVE' AND expiry_notified_at IS NULL;
