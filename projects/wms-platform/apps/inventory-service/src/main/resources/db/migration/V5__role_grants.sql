-- W2 enforcement: inventory_movement is append-only.
--
-- Authoritative reference: rules/domains/wms.md (W2),
-- specs/services/inventory-service/domain-model.md §2.
--
-- Two-layer defense:
--   1. SQL trigger (always enforced regardless of role / owner). PostgreSQL
--      table owners bypass GRANT/REVOKE ACLs, so a REVOKE alone is a no-op
--      whenever the application role also owns the table (the case in the
--      local docker-compose setup). The trigger rejects UPDATE/DELETE for
--      every non-superuser.
--   2. REVOKE UPDATE, DELETE on inventory_movement from the application role.
--      Effective in production where the DBA separates an `inventory_owner`
--      DDL role from a runtime application role; documented as best-practice.
--
-- The Testcontainers test that verifies rejection must run as a non-superuser
-- connection so the trigger fires (superusers bypass triggers via the
-- `session_replication_role = replica` switch, which the test does NOT use).

CREATE OR REPLACE FUNCTION inventory_movement_reject_modification()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION
        'inventory_movement is append-only (W2): % rejected',
        TG_OP
        USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER trg_inventory_movement_no_update
    BEFORE UPDATE ON inventory_movement
    FOR EACH ROW
    EXECUTE FUNCTION inventory_movement_reject_modification();

CREATE TRIGGER trg_inventory_movement_no_delete
    BEFORE DELETE ON inventory_movement
    FOR EACH ROW
    EXECUTE FUNCTION inventory_movement_reject_modification();

-- Best-effort REVOKE for production deployments where the runtime role is
-- not the table owner. The block tolerates absence of the role (local dev).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = current_user) THEN
        EXECUTE format(
            'REVOKE UPDATE, DELETE ON inventory_movement FROM %I',
            current_user);
    END IF;
EXCEPTION WHEN OTHERS THEN
    -- Owner self-revoke is a no-op in PostgreSQL; tolerate any related error
    -- to keep the migration idempotent across environments.
    NULL;
END $$;
