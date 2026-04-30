-- Flyway handles triggers: single-statement triggers don't need BEGIN/END or DELIMITER
-- These triggers enforce append-only immutability on account_status_history (A3)

-- Note: For MySQL single-statement triggers, no BEGIN...END is needed.
-- Flyway's default semicolon separator works because SIGNAL is the only statement in the trigger body.
-- However, if Flyway splits on the semicolon inside the trigger, we need to handle this.
-- We use two separate CREATE TRIGGER statements, each as a single statement without BEGIN/END.

CREATE TRIGGER trg_account_status_history_no_update
BEFORE UPDATE ON account_status_history FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'UPDATE on account_status_history is forbidden (append-only)';

CREATE TRIGGER trg_account_status_history_no_delete
BEFORE DELETE ON account_status_history FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'DELETE on account_status_history is forbidden (append-only)';
