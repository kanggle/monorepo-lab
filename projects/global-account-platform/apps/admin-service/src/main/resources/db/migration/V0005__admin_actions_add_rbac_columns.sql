-- TASK-BE-028a: extend admin_actions with RBAC context columns.
-- operator_id starts nullable because no admin_operators rows exist yet at V0005
-- apply time (seed lands in V0006, BIGINT PK migration in V0007/V0008).
-- DENIED is added to the set of outcome values; admin_actions.outcome is already
-- VARCHAR(20), so no ENUM widening is required.

ALTER TABLE admin_actions
    ADD COLUMN operator_id     VARCHAR(36) NULL AFTER actor_role,
    ADD COLUMN permission_used VARCHAR(80) NULL AFTER operator_id;

CREATE INDEX idx_admin_actions_operator_time ON admin_actions (operator_id, started_at);
CREATE INDEX idx_admin_actions_outcome_time  ON admin_actions (outcome, started_at);
