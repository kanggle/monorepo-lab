-- TASK-MONO-773 S3 (owner decision OD-4 · ADR-MONO-083 OD-5 P9) — admit the ORIGIN source on operator_domain_set_grant.
--
-- A company's first administrator receives ONE row (operator, the new tenant, preset all-domains-full, 'ORIGIN',
-- granted_by NULL) inside the company-creation atomic bundle (onboarding-api.md «회사 생성자의 업무 권한 세트» ·
-- data-model.md § operator_domain_set_grant). V0052 created the source CHECK as
--     source_key IN ('DIRECT', 'MIGRATION') OR (source_key LIKE 'GROUP:%' AND tenant_id <> '*')
-- so every ORIGIN insert is a CHECK violation today. This file:
--   1. drops ck_operator_domain_set_grant_source — only while its clause does not yet name ORIGIN (re-run safe);
--   2. re-adds it under the same name with ORIGIN in the vocabulary;
--   3. adds ck_operator_domain_set_grant_origin_not_platform — an ORIGIN row never targets '*'
--      (a company's first administrator is a company member; '*' is not a company — ADR-082 D2).
-- Neither CHECK touches group_origin (it carries ON DELETE CASCADE — MySQL refuses it in a CHECK, ER 3823, V0052).
-- Existing data cannot violate either: no ORIGIN row can exist before this file (the old CHECK refused it).
--
-- No data change: ORIGIN is not backfilled — companies created before 773 keep their V0052 MIGRATION rows
-- (data-model.md «백필도 만들지 않는다»).
--
-- Shape contract (V0016 cycle-3 lesson, same as V0042 / V0044 / V0051 / V0053): forward-only, no rollback; each DDL
-- guard is one self-contained SET / PREPARE / EXECUTE / DEALLOCATE quartet derived from INFORMATION_SCHEMA (no user
-- variable read by a later statement); NULL-safe guards (`(SELECT COUNT(*) …)`). MySQL-only (8.0.19+ DROP CHECK).

-- ========================================================================
-- 1. Drop the V0052 source CHECK — only while it does not admit ORIGIN
-- ========================================================================
SET @ddl_odsg_src_drop := (
    SELECT IF(
        (SELECT COUNT(*)
           FROM information_schema.check_constraints
          WHERE constraint_schema = DATABASE()
            AND constraint_name   = 'ck_operator_domain_set_grant_source'
            AND check_clause NOT LIKE '%ORIGIN%') > 0,
        'ALTER TABLE operator_domain_set_grant DROP CHECK ck_operator_domain_set_grant_source',
        'SELECT 1'
    )
);
PREPARE stmt_odsg_src_drop FROM @ddl_odsg_src_drop;
EXECUTE stmt_odsg_src_drop;
DEALLOCATE PREPARE stmt_odsg_src_drop;

-- ========================================================================
-- 2. Re-add it with ORIGIN in the vocabulary
-- ========================================================================
SET @ddl_odsg_src_add := (
    SELECT IF(
        (SELECT COUNT(*)
           FROM information_schema.table_constraints
          WHERE constraint_schema = DATABASE()
            AND table_name        = 'operator_domain_set_grant'
            AND constraint_name   = 'ck_operator_domain_set_grant_source') = 0,
        'ALTER TABLE operator_domain_set_grant ADD CONSTRAINT ck_operator_domain_set_grant_source CHECK (source_key IN (''DIRECT'', ''MIGRATION'', ''ORIGIN'') OR (source_key LIKE ''GROUP:%'' AND tenant_id <> ''*''))',
        'SELECT 1'
    )
);
PREPARE stmt_odsg_src_add FROM @ddl_odsg_src_add;
EXECUTE stmt_odsg_src_add;
DEALLOCATE PREPARE stmt_odsg_src_add;

-- ========================================================================
-- 3. An ORIGIN row is never a '*' row
-- ========================================================================
SET @ddl_odsg_origin_chk := (
    SELECT IF(
        (SELECT COUNT(*)
           FROM information_schema.table_constraints
          WHERE constraint_schema = DATABASE()
            AND table_name        = 'operator_domain_set_grant'
            AND constraint_name   = 'ck_operator_domain_set_grant_origin_not_platform') = 0,
        'ALTER TABLE operator_domain_set_grant ADD CONSTRAINT ck_operator_domain_set_grant_origin_not_platform CHECK (source_key <> ''ORIGIN'' OR tenant_id <> ''*'')',
        'SELECT 1'
    )
);
PREPARE stmt_odsg_origin_chk FROM @ddl_odsg_origin_chk;
EXECUTE stmt_odsg_origin_chk;
DEALLOCATE PREPARE stmt_odsg_origin_chk;
