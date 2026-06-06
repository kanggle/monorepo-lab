-- TASK-BE-029-2: TOTP enrollment schema + admin_actions.twofa_used.
--
-- 1. admin_operator_totp — AES-GCM encrypted TOTP shared secret (1:1 with
--    admin_operators). Layout of secret_encrypted:
--        [ 12-byte IV ][ ciphertext ][ 16-byte auth tag ]
--    AAD during encrypt/decrypt is the big-endian 8-byte operator BIGINT PK
--    (row-swap defense). See specs/services/admin-service/security.md
--    "TOTP Secret Encryption".
--
-- 2. admin_actions.twofa_used BOOLEAN — populated on the login path in 029-3.
--    For 2FA enroll/verify audit rows it is always FALSE (not a login).
--
-- 3. trg_admin_actions_finalize_only — extend the column guard so that
--    twofa_used cannot mutate after the IN_PROGRESS → terminal transition.
--
-- 4. admin_roles seed: require_2fa=TRUE for SUPER_ADMIN and SECURITY_ANALYST
--    (column already exists from V0004).

-- --- 1. admin_operator_totp -----------------------------------------------

CREATE TABLE admin_operator_totp (
    operator_id           BIGINT         NOT NULL,
    secret_encrypted      VARBINARY(512) NOT NULL,
    secret_key_id         VARCHAR(64)    NOT NULL DEFAULT 'v1',
    recovery_codes_hashed TEXT           NOT NULL,
    enrolled_at           DATETIME(6)    NOT NULL,
    last_used_at          DATETIME(6)    NULL,
    version               BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (operator_id),
    CONSTRAINT fk_admin_operator_totp_operator
        FOREIGN KEY (operator_id) REFERENCES admin_operators(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --- 2. admin_actions.twofa_used ------------------------------------------

ALTER TABLE admin_actions
    ADD COLUMN twofa_used BOOLEAN NOT NULL DEFAULT FALSE;

-- --- 3. finalize-only trigger with twofa_used guard -----------------------

DROP TRIGGER IF EXISTS trg_admin_actions_finalize_only;

CREATE TRIGGER trg_admin_actions_finalize_only
BEFORE UPDATE ON admin_actions FOR EACH ROW
BEGIN
    IF OLD.outcome <> 'IN_PROGRESS' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'UPDATE on admin_actions is forbidden once outcome is terminal (append-only)';
    END IF;
    IF NEW.outcome = 'IN_PROGRESS' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'UPDATE must transition outcome from IN_PROGRESS to a terminal state';
    END IF;
    IF NEW.id <> OLD.id
        OR NEW.action_code <> OLD.action_code
        OR NEW.actor_id <> OLD.actor_id
        OR NEW.actor_role <> OLD.actor_role
        OR NOT (NEW.operator_id <=> OLD.operator_id)
        OR NOT (NEW.permission_used <=> OLD.permission_used)
        OR NOT (NEW.legacy_audit_id <=> OLD.legacy_audit_id)
        OR NEW.target_type <> OLD.target_type
        OR NEW.target_id <> OLD.target_id
        OR NEW.reason <> OLD.reason
        OR NOT (NEW.ticket_id <=> OLD.ticket_id)
        OR NEW.idempotency_key <> OLD.idempotency_key
        OR NEW.started_at <> OLD.started_at
        OR NEW.twofa_used <> OLD.twofa_used
    THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Only outcome, downstream_detail, and completed_at may be updated on admin_actions';
    END IF;
END;

-- --- 4. seed require_2fa = TRUE on privileged roles -----------------------

UPDATE admin_roles
   SET require_2fa = TRUE
 WHERE name IN ('SUPER_ADMIN', 'SECURITY_ANALYST');
