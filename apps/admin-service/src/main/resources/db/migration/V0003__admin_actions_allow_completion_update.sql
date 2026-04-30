-- Allow transition from IN_PROGRESS to a final outcome (SUCCESS | FAILURE | DENIED).
-- This supports the audit-before-downstream pattern (A10 fail-closed):
--   1. INSERT row with outcome='IN_PROGRESS' BEFORE the downstream HTTP call
--   2. After downstream completes (or fails), UPDATE outcome to a terminal state
--
-- The append-only invariant (A3) is preserved for:
--   - any row whose current outcome is already a terminal state
--   - any column other than outcome, downstream_detail, completed_at
--
-- DELETE remains forbidden for all rows.

DROP TRIGGER IF EXISTS trg_admin_actions_no_update;

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
