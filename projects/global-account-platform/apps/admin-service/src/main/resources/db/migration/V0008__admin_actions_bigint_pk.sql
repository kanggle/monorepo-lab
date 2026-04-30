-- TASK-BE-028b1: migrate admin_actions PK from VARCHAR(36) UUID to BIGINT AUTO_INCREMENT.
-- Preserve the original UUID in legacy_audit_id for API backward compatibility
-- (response DTOs continue to return the UUID string as auditId).
-- operator_id switches from VARCHAR(36) to BIGINT (FK to admin_operators.id).
--
-- Dev data is assumed empty: any existing admin_actions.operator_id (VARCHAR) values
-- cannot be mapped to the new BIGINT admin_operators.id without a living operator row,
-- so we reset them to NULL and let the new write path populate fresh rows.

-- 1. Drop the append-only/finalize trigger so we can restructure the table.
DROP TRIGGER IF EXISTS trg_admin_actions_finalize_only;
DROP TRIGGER IF EXISTS trg_admin_actions_no_delete;

-- 2. Add new surrogate BIGINT key column and legacy UUID preservation column.
ALTER TABLE admin_actions
    ADD COLUMN new_id          BIGINT      NOT NULL AUTO_INCREMENT UNIQUE AFTER id,
    ADD COLUMN legacy_audit_id VARCHAR(36) NULL           AFTER new_id;

-- 3. Copy existing UUIDs into the preservation column.
UPDATE admin_actions SET legacy_audit_id = id;

-- 4. Promote new_id to PK and drop the old UUID column.
ALTER TABLE admin_actions DROP PRIMARY KEY;
ALTER TABLE admin_actions DROP COLUMN id;
ALTER TABLE admin_actions CHANGE COLUMN new_id id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY;

-- 5. Convert operator_id to BIGINT. Existing VARCHAR values (if any) cannot be
--    mapped to admin_operators(id); null them out so the FK can be added.
ALTER TABLE admin_actions MODIFY COLUMN operator_id BIGINT NULL;
UPDATE admin_actions SET operator_id = NULL;

ALTER TABLE admin_actions
    ADD CONSTRAINT fk_admin_actions_operator
        FOREIGN KEY (operator_id) REFERENCES admin_operators(id);

-- 6. Add index on legacy_audit_id so the application layer can look up the
--    IN_PROGRESS row by the UUID surfaced to API callers (LockAccountResponse etc.).
CREATE UNIQUE INDEX uk_admin_actions_legacy_audit_id ON admin_actions (legacy_audit_id);

-- 7. Recreate the append-only triggers. Column semantics unchanged; the finalize
--    trigger already uses column comparisons (NEW.id <> OLD.id etc.) which remain
--    valid under the BIGINT type.
CREATE TRIGGER trg_admin_actions_no_delete
BEFORE DELETE ON admin_actions FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'DELETE on admin_actions is forbidden (append-only)';

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
        OR NOT (NEW.legacy_audit_id <=> OLD.legacy_audit_id)
        OR NEW.target_type <> OLD.target_type
        OR NEW.target_id <> OLD.target_id
        OR NEW.reason <> OLD.reason
        OR NOT (NEW.ticket_id <=> OLD.ticket_id)
        OR NEW.idempotency_key <> OLD.idempotency_key
        OR NEW.started_at <> OLD.started_at
    THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Only outcome, downstream_detail, and completed_at may be updated on admin_actions';
    END IF;
END;
