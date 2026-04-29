-- W2 enforcement: append-only ledger / dedupe tables.
-- Tables protected:
--   - outbound_outbox            (block DELETE only; publisher needs UPDATE for published_at)
--   - outbound_event_dedupe      (block UPDATE and DELETE)
--   - erp_order_webhook_dedupe   (block UPDATE and DELETE)
--   - tms_request_dedupe         (block UPDATE and DELETE)
--
-- Two-layer defense: trigger (always enforced) + REVOKE for production
-- runtime roles where the runtime role is not the table owner.

CREATE OR REPLACE FUNCTION outbound_event_dedupe_reject_modification()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION
        'outbound_event_dedupe is append-only (W2): % rejected', TG_OP
        USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER trg_outbound_event_dedupe_no_update
    BEFORE UPDATE ON outbound_event_dedupe
    FOR EACH ROW EXECUTE FUNCTION outbound_event_dedupe_reject_modification();

CREATE TRIGGER trg_outbound_event_dedupe_no_delete
    BEFORE DELETE ON outbound_event_dedupe
    FOR EACH ROW EXECUTE FUNCTION outbound_event_dedupe_reject_modification();

CREATE OR REPLACE FUNCTION erp_order_webhook_dedupe_reject_modification()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION
        'erp_order_webhook_dedupe is append-only (W2): % rejected', TG_OP
        USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER trg_erp_order_webhook_dedupe_no_update
    BEFORE UPDATE ON erp_order_webhook_dedupe
    FOR EACH ROW EXECUTE FUNCTION erp_order_webhook_dedupe_reject_modification();

CREATE TRIGGER trg_erp_order_webhook_dedupe_no_delete
    BEFORE DELETE ON erp_order_webhook_dedupe
    FOR EACH ROW EXECUTE FUNCTION erp_order_webhook_dedupe_reject_modification();

CREATE OR REPLACE FUNCTION tms_request_dedupe_reject_modification()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION
        'tms_request_dedupe is append-only (W2): % rejected', TG_OP
        USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER trg_tms_request_dedupe_no_update
    BEFORE UPDATE ON tms_request_dedupe
    FOR EACH ROW EXECUTE FUNCTION tms_request_dedupe_reject_modification();

CREATE TRIGGER trg_tms_request_dedupe_no_delete
    BEFORE DELETE ON tms_request_dedupe
    FOR EACH ROW EXECUTE FUNCTION tms_request_dedupe_reject_modification();

-- outbound_outbox: block DELETE only (publisher needs UPDATE for published_at).
CREATE OR REPLACE FUNCTION outbound_outbox_reject_delete()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION
        'outbound_outbox is append-only (W2): DELETE rejected'
        USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER trg_outbound_outbox_no_delete
    BEFORE DELETE ON outbound_outbox
    FOR EACH ROW EXECUTE FUNCTION outbound_outbox_reject_delete();

-- Best-effort REVOKE for production runtime roles.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = current_user) THEN
        EXECUTE format('REVOKE UPDATE, DELETE ON outbound_event_dedupe FROM %I', current_user);
        EXECUTE format('REVOKE UPDATE, DELETE ON erp_order_webhook_dedupe FROM %I', current_user);
        EXECUTE format('REVOKE UPDATE, DELETE ON tms_request_dedupe FROM %I', current_user);
        EXECUTE format('REVOKE DELETE ON outbound_outbox FROM %I', current_user);
    END IF;
EXCEPTION WHEN OTHERS THEN
    NULL;
END $$;
