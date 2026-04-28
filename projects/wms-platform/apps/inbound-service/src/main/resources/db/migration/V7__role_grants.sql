-- W2 enforcement: append-only ledger / dedupe / confirmation tables.
--
-- Authoritative reference: rules/domains/wms.md (W2),
-- specs/services/inbound-service/domain-model.md §5 / §6 / §8 / §4
-- (PutawayConfirmation).
--
-- Tables protected:
--   - inbound_outbox               (T3 outbox; never updated after publish-stamp)
--   - inbound_event_dedupe         (T8 dedupe; PK is sole identity)
--   - erp_webhook_dedupe           (webhook replay protection)
--   - putaway_confirmation         (per-line physical confirmation;
--                                   "No updates after creation" — domain-model.md §4)
--
-- Note: inbound_outbox carries a published_at column written by the publisher.
-- That UPDATE is performed by the publisher process which connects under a
-- separate role (DBA-managed) — the application role only INSERTs. The
-- publisher's role exclusion is a deployment-time concern; in this migration
-- we only revoke UPDATE/DELETE from the runtime application role to enforce
-- the invariant for ordinary use-case paths.
--
-- Two-layer defense (mirrors inventory-service V5):
--   1. SQL trigger (always enforced regardless of role / owner). PostgreSQL
--      table owners bypass GRANT/REVOKE ACLs, so a REVOKE alone is a no-op
--      whenever the application role also owns the table (the case in the
--      local docker-compose setup). The trigger rejects UPDATE/DELETE for
--      every non-superuser session.
--   2. REVOKE UPDATE, DELETE from the runtime application role. Effective in
--      production where the DBA separates an `inbound_owner` DDL role from a
--      runtime application role; documented as best-practice.
--
-- For the outbox, we only block DELETE — the publisher needs UPDATE on
-- published_at. The trigger lets UPDATE through for outbox but blocks DELETE.

-- ---------------------------------------------------------------------------
-- inbound_event_dedupe — block UPDATE and DELETE
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION inbound_event_dedupe_reject_modification()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION
        'inbound_event_dedupe is append-only (W2): % rejected',
        TG_OP
        USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER trg_inbound_event_dedupe_no_update
    BEFORE UPDATE ON inbound_event_dedupe
    FOR EACH ROW
    EXECUTE FUNCTION inbound_event_dedupe_reject_modification();

CREATE TRIGGER trg_inbound_event_dedupe_no_delete
    BEFORE DELETE ON inbound_event_dedupe
    FOR EACH ROW
    EXECUTE FUNCTION inbound_event_dedupe_reject_modification();

-- ---------------------------------------------------------------------------
-- erp_webhook_dedupe — block UPDATE and DELETE
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION erp_webhook_dedupe_reject_modification()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION
        'erp_webhook_dedupe is append-only (W2): % rejected',
        TG_OP
        USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER trg_erp_webhook_dedupe_no_update
    BEFORE UPDATE ON erp_webhook_dedupe
    FOR EACH ROW
    EXECUTE FUNCTION erp_webhook_dedupe_reject_modification();

CREATE TRIGGER trg_erp_webhook_dedupe_no_delete
    BEFORE DELETE ON erp_webhook_dedupe
    FOR EACH ROW
    EXECUTE FUNCTION erp_webhook_dedupe_reject_modification();

-- ---------------------------------------------------------------------------
-- putaway_confirmation — block UPDATE and DELETE (domain-model §4 invariant)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION putaway_confirmation_reject_modification()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION
        'putaway_confirmation is append-only (W2): % rejected',
        TG_OP
        USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER trg_putaway_confirmation_no_update
    BEFORE UPDATE ON putaway_confirmation
    FOR EACH ROW
    EXECUTE FUNCTION putaway_confirmation_reject_modification();

CREATE TRIGGER trg_putaway_confirmation_no_delete
    BEFORE DELETE ON putaway_confirmation
    FOR EACH ROW
    EXECUTE FUNCTION putaway_confirmation_reject_modification();

-- ---------------------------------------------------------------------------
-- inbound_outbox — block DELETE only (publisher needs UPDATE for published_at)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION inbound_outbox_reject_delete()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION
        'inbound_outbox is append-only (W2): DELETE rejected'
        USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER trg_inbound_outbox_no_delete
    BEFORE DELETE ON inbound_outbox
    FOR EACH ROW
    EXECUTE FUNCTION inbound_outbox_reject_delete();

-- ---------------------------------------------------------------------------
-- Best-effort REVOKE for production deployments where the runtime role is
-- not the table owner. Tolerates absence of separate roles in local dev.
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = current_user) THEN
        EXECUTE format(
            'REVOKE UPDATE, DELETE ON inbound_event_dedupe FROM %I',
            current_user);
        EXECUTE format(
            'REVOKE UPDATE, DELETE ON erp_webhook_dedupe FROM %I',
            current_user);
        EXECUTE format(
            'REVOKE UPDATE, DELETE ON putaway_confirmation FROM %I',
            current_user);
        EXECUTE format(
            'REVOKE DELETE ON inbound_outbox FROM %I',
            current_user);
    END IF;
EXCEPTION WHEN OTHERS THEN
    -- Owner self-revoke is a no-op in PostgreSQL; tolerate any related error
    -- to keep the migration idempotent across environments.
    NULL;
END $$;
