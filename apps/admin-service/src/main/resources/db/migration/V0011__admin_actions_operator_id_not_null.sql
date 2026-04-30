-- TASK-BE-028b2-fix: enforce admin_actions.operator_id NOT NULL per
-- specs/services/admin-service/data-model.md.
--
-- Background: V0008 converted operator_id from VARCHAR(36) to BIGINT but kept
-- it NULL because the UUID→BIGINT resolution path (AdminActionAuditor) had not
-- yet been implemented. TASK-BE-028b2-fix lands that resolution and makes
-- every INSERT populate the FK. This migration tightens the schema to match.
--
-- Safety: this branch is the first to create admin_actions with BIGINT
-- operator_id, so no historical rows need back-filling. The statement would
-- fail loudly if any row carries operator_id IS NULL, which is the correct
-- fail-closed behaviour for audit-heavy A10.

ALTER TABLE admin_actions
    MODIFY COLUMN operator_id BIGINT NOT NULL;
