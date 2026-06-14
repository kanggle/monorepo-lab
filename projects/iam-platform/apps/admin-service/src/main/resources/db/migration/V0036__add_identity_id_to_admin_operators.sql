-- TASK-BE-373 (ADR-MONO-034 U6 step 3c): the operator extension's link column to
-- the central `identities` registry (step 3a, account_db / account-service V0023).
--
-- `identity_id` is the cross-store correlation key (value-convention — admin_db is a
-- physically separate database from account_db, so NO cross-DB foreign key is possible;
-- the only in-DB FK lives within account_db from accounts -> identities). A populated
-- value means "this operator is the same person as the central identity it points to";
-- NULL (the default for every existing row) means the operator is not yet linked.
--
-- NO BACKFILL HERE. ADR-034 U3 mandates the link be opt-in / explicit / audited /
-- reversible — a silent same-email merge is the cross-tenant privilege-escalation
-- vector D6-A forbids (§ 1.3). The `oidc_subject` -> identity backfill is therefore
-- performed exclusively via the explicit, audited link surface
-- (PATCH /api/admin/operators/{operatorId}/identity:link), never as a migration step.
-- This also keeps the migration additive + net-zero + reversible (no row depends on the
-- column until the link operation is invoked), and avoids a cross-DB read at migrate time.
--
-- Net-zero: additive nullable column + non-unique index; existing operators are all
-- unlinked until the U3 link operation is explicitly invoked.

-- MySQL column_definition order: the COMMENT clause must precede the column
-- position (AFTER) clause — `... NULL COMMENT '...' AFTER col`, NOT
-- `... NULL AFTER col COMMENT '...'` (the latter is a SQLSyntaxError).
ALTER TABLE admin_operators
    ADD COLUMN identity_id VARCHAR(36) NULL
        COMMENT 'ADR-034 U3 (TASK-BE-373): central identities.identity_id this operator is linked to (value-convention cross-DB ref to account_db; NULL = unlinked). Set/cleared only via the opt-in audited link/unlink surface.'
        AFTER oidc_subject;

CREATE INDEX idx_admin_operators_identity_id ON admin_operators (identity_id);
