-- TASK-MONO-046-5: redefine trg_login_history_no_update to permit GDPR-driven
-- PII masking UPDATE while preserving the append-only invariant for all other
-- callers. PiiMaskingService sets @pii_masking_bypass=1 before its UPDATE and
-- resets it in finally; every other connection sees the trigger reject UPDATEs
-- as before (SQLSTATE 45000).
--
-- Pattern mirrors apps/admin-service V0010 — Flyway 10 Community handles
-- BEGIN/END trigger bodies natively, no DELIMITER directive needed.
--
-- Audit invariant change (specs/services/security-service/): UPDATE on
-- login_history is forbidden EXCEPT when the connection has set
-- @pii_masking_bypass=1, which only PiiMaskingService does inside a
-- single transaction (GDPR Article 17 erasure). All other UPDATE attempts
-- (operator, ops console, ad-hoc DBA) still raise SQLSTATE 45000.

DROP TRIGGER IF EXISTS trg_login_history_no_update;

CREATE TRIGGER trg_login_history_no_update
BEFORE UPDATE ON login_history FOR EACH ROW
BEGIN
    IF @pii_masking_bypass IS NULL OR @pii_masking_bypass <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'UPDATE not allowed on login_history (append-only)';
    END IF;
END;
