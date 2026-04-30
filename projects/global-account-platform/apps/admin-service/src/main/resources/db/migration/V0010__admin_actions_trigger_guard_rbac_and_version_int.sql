-- TASK-BE-028b2: two small carry-over fixes.
--
-- 1. Extend trg_admin_actions_finalize_only to guard the RBAC columns that V0005
--    added (operator_id, permission_used). The V0008 trigger recreated the
--    finalize-only trigger but kept the V0001-era column guard list, so those
--    two columns were mutable after IN_PROGRESS→terminal transitions. That
--    violates the append-only invariant (A3) for the only writable transition.
--
-- 2. admin_operators.version is specified as INT in data-model.md (optimistic
--    lock counter); the V0004/V0007 DDL created it as BIGINT. Narrow it to INT
--    to match the spec. No data conversion loss is possible (version = 0 at
--    creation and monotonically increments, well within INT range).

-- --- 1. Trigger rewrite ----------------------------------------------------

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
    THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Only outcome, downstream_detail, and completed_at may be updated on admin_actions';
    END IF;
END;

-- --- 2. admin_operators.version: BIGINT -> INT ----------------------------

ALTER TABLE admin_operators
    MODIFY COLUMN version INT NOT NULL DEFAULT 0;
