-- TASK-BE-631 (ADR-MONO-083 P2 · OD-1 default · OD-3 owner decision 2026-10-14 UTC) — domain permission sets:
-- the named bundles of DOMAIN work roles (catalog entries) that are granted to an operator PER TENANT, and the
-- «이전 호환» backfill that keeps every existing operator on exactly today's behaviour.
-- Canonical shapes: data-model.md § domain_permission_set … § operator_group_domain_set_grant; the preset table,
-- the source_key revoke rules and the wire shapes: admin-api.md § 도메인 업무 권한 세트.
--
-- 🔴 NET-ZERO FOR TOKENS. auth-service does not read any of this until P3 (TASK-BE-632): the assume token's
-- `roles` stay subscription-derived. This file creates tables, read-only presets, a permission-key mapping
-- assigned to nobody new, and MIGRATION rows that P3 will read as «legacy-compat = the whole catalog».
--
-- Order (data-model.md § Migration Strategy V0052 — kept as written):
--   1. four tables (CREATE TABLE IF NOT EXISTS — the house form for whole-table creation, V0043 / V0047);
--   2. system presets with FIXED set ids (INSERT IGNORE — same ids in every environment);
--   3. `domain_access.manage` → SUPER_ADMIN · TENANT_ADMIN (INSERT IGNORE, inert — V0045 / V0048 discipline);
--   4. the backfill (INSERT … SELECT … WHERE NOT EXISTS — a re-run inserts 0 rows):
--        (a) every MEMBERSHIP (operator_tenant_assignment row) of a non-'*' operator → (operator, tenant,
--            legacy-compat, MIGRATION);
--        (b) every '*' operator → (operator, '*', legacy-compat, MIGRATION) (OD-3).
--      Status-independent (a SUSPENDED operator keeps its rows for when it is re-activated). A membership created
--      AFTER this file ran starts from zero sets — there is no code branch for it, there is simply no row.
--
-- WHY «legacy-compat = every catalog role» (BE-631 AC-0 interpretation, recorded): admin_db cannot read
-- account_db's subscriptions, so the backfill cannot give «the sets of the tenant's entitled domains». P3 issues
-- `domainRoles ∩ derive(entitled ∩ ceiling)`, so «every role + issuance intersection» yields exactly the role set
-- the subscription derivation yields today — including after a later subscription change, which a
-- per-domain backfill would NOT track. Same result as ADR-083 D8's wording; no code branch.
--
-- 🔴 db/migration-dev R__ seeds run AFTER every V migration (784 D-2 lesson), so on a fresh volume the seeded
-- demo operators do not exist yet when step 4 runs: R__seed_zz_demo_domain_permission_sets.sql gives them the
-- same MIGRATION rows.
--
-- Shape notes:
--   * operator_domain_set_grant's PRIMARY KEY carries the SOURCE (`source_key` — DIRECT | MIGRATION |
--     GROUP:<operator_group.id>, never NULL): direct + G1 + G2 granting the same set are three rows, and revoking
--     one source deletes that source's row only (ADR-083 D4). The admin_operator_roles shape (source = one
--     nullable column outside the key, first writer owns it) is exactly what this avoids.
--   * The data-model CHECK pairing `group_origin` with `source_key` cannot be declared as written: MySQL 8 refuses a
--     CHECK on a column that carries a foreign-key referential action (ER 3823 — `group_origin` is
--     FK … ON DELETE CASCADE, the group-delete safety net data-model requires). data-model.md allows exactly
--     this fallback («MySQL 이 이 식을 CHECK 로 받지 못하면 같은 불변식을 애플리케이션 + 비-Docker 형태 시험으로
--     고정한다»): the CHECK below pins the half that touches no FK column (the source vocabulary and «a GROUP row
--     is never in '*'»), the application writes `group_origin` together with `source_key`, and
--     V0052MigrationShapeTest pins both.
--   * Forward-only. No cross-statement MySQL user variable (V0016 cycle-2 trap). MySQL-only.

-- ========================================================================
-- 1. Tables
-- ========================================================================

-- A named bundle of catalog roles: a tenant set (tenant_id = the owning tenant) or a system preset
-- (tenant_id NULL, system_key set). '*' never owns a set — platform-wide bundles are presets.
CREATE TABLE IF NOT EXISTS domain_permission_set (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    set_id      VARCHAR(36)  NOT NULL,              -- external UUID (API setId), presets use fixed values
    tenant_id   VARCHAR(32)  NULL,                  -- owning tenant, NULL = system preset
    system_key  VARCHAR(40)  NULL,                  -- preset key, NULL = tenant set
    name        VARCHAR(120) NOT NULL,
    description VARCHAR(255) NULL,
    created_by  BIGINT       NULL,                  -- creating operator, NULL for presets (seed)
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    version     INT          NOT NULL DEFAULT 0,    -- optimistic lock (concurrent edit / delete)
    PRIMARY KEY (id),
    UNIQUE KEY uk_domain_permission_set_set_id (set_id),
    UNIQUE KEY uk_domain_permission_set_system_key (system_key),
    KEY idx_domain_permission_set_tenant (tenant_id),
    CONSTRAINT ck_domain_permission_set_tenant_not_platform CHECK (tenant_id IS NULL OR tenant_id <> '*'),
    CONSTRAINT ck_domain_permission_set_preset_iff_no_tenant CHECK ((tenant_id IS NULL) = (system_key IS NOT NULL)),
    CONSTRAINT fk_domain_permission_set_created_by FOREIGN KEY (created_by) REFERENCES admin_operators(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Set → catalog role. Values are checked against the code catalog (DomainRoleCatalog) — no catalog table.
CREATE TABLE IF NOT EXISTS domain_permission_set_role (
    set_id      BIGINT      NOT NULL,               -- FK → domain_permission_set.id (surrogate)
    domain_key  VARCHAR(32) NOT NULL,
    role_name   VARCHAR(40) NOT NULL,
    PRIMARY KEY (set_id, domain_key, role_name),
    CONSTRAINT fk_domain_permission_set_role_set FOREIGN KEY (set_id) REFERENCES domain_permission_set(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Operator × tenant × set × SOURCE. Effective work roles of (O, T) = the roles of the sets of O's T rows
-- (∪ O's '*' rows when O is a '*' operator).
CREATE TABLE IF NOT EXISTS operator_domain_set_grant (
    operator_id  BIGINT      NOT NULL,              -- FK → admin_operators.id
    tenant_id    VARCHAR(32) NOT NULL,              -- a membership of a non-'*' operator, concrete or '*' for a '*' operator
    set_id       BIGINT      NOT NULL,              -- FK → domain_permission_set.id (RESTRICT — 409 IN_USE first)
    source_key   VARCHAR(48) NOT NULL,              -- DIRECT | MIGRATION | GROUP:<operator_group.id>
    group_origin BIGINT      NULL,                  -- = the group id of a GROUP:<id> row, NULL otherwise
    granted_by   BIGINT      NULL,                  -- granting operator, NULL for MIGRATION
    granted_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (operator_id, tenant_id, set_id, source_key),
    KEY idx_operator_domain_set_grant_tenant_set (tenant_id, set_id),
    KEY idx_operator_domain_set_grant_group (group_origin),
    KEY idx_operator_domain_set_grant_set (set_id),
    CONSTRAINT ck_operator_domain_set_grant_source CHECK (
        source_key IN ('DIRECT', 'MIGRATION')
     OR (source_key LIKE 'GROUP:%' AND tenant_id <> '*')
    ),
    CONSTRAINT fk_operator_domain_set_grant_operator   FOREIGN KEY (operator_id)  REFERENCES admin_operators(id)       ON DELETE CASCADE,
    CONSTRAINT fk_operator_domain_set_grant_set        FOREIGN KEY (set_id)       REFERENCES domain_permission_set(id) ON DELETE RESTRICT,
    CONSTRAINT fk_operator_domain_set_grant_group      FOREIGN KEY (group_origin) REFERENCES operator_group(id)        ON DELETE CASCADE,
    CONSTRAINT fk_operator_domain_set_grant_granted_by FOREIGN KEY (granted_by)   REFERENCES admin_operators(id)       ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- A set granted to a group (the TEMPLATE fanned out to members as GROUP:<id> rows — slice B writes it).
CREATE TABLE IF NOT EXISTS operator_group_domain_set_grant (
    group_id    BIGINT      NOT NULL,               -- FK → operator_group.id (surrogate)
    set_id      BIGINT      NOT NULL,               -- FK → domain_permission_set.id
    granted_by  BIGINT      NULL,
    granted_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (group_id, set_id),
    KEY idx_operator_group_domain_set_grant_set (set_id),
    CONSTRAINT fk_operator_group_domain_set_grant_group      FOREIGN KEY (group_id)   REFERENCES operator_group(id)        ON DELETE CASCADE,
    CONSTRAINT fk_operator_group_domain_set_grant_set        FOREIGN KEY (set_id)     REFERENCES domain_permission_set(id) ON DELETE RESTRICT,
    CONSTRAINT fk_operator_group_domain_set_grant_granted_by FOREIGN KEY (granted_by) REFERENCES admin_operators(id)       ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================================================
-- 2. System presets (read-only; fixed set ids — admin-api.md § 시스템 프리셋)
-- ========================================================================
INSERT IGNORE INTO domain_permission_set
    (set_id, tenant_id, system_key, name, description, created_by, created_at, updated_at, version)
VALUES
    ('00000083-0000-7000-8000-000000000001', NULL, 'wms-inventory-read',    '재고 조회',            'WMS 재고 조회',                         NULL, NOW(6), NOW(6), 0),
    ('00000083-0000-7000-8000-000000000002', NULL, 'wms-inventory-operate', '재고 운영',            'WMS 재고 조회 · 변경',                  NULL, NOW(6), NOW(6), 0),
    ('00000083-0000-7000-8000-000000000003', NULL, 'wms-inbound-operate',   '입고 운영',            'WMS 입고 조회 · 변경',                  NULL, NOW(6), NOW(6), 0),
    ('00000083-0000-7000-8000-000000000004', NULL, 'wms-outbound-operate',  '출고 운영',            'WMS 출고 조회 · 변경',                  NULL, NOW(6), NOW(6), 0),
    ('00000083-0000-7000-8000-000000000005', NULL, 'wms-master-read',       '기준정보 조회',        'WMS 기준정보 조회',                     NULL, NOW(6), NOW(6), 0),
    ('00000083-0000-7000-8000-000000000006', NULL, 'wms-ops-actions',       'WMS 운영 조치',        'WMS 알림 확인 · 운영 조치',             NULL, NOW(6), NOW(6), 0),
    ('00000083-0000-7000-8000-000000000007', NULL, 'wms-full',              'WMS 전체',             'WMS 카탈로그 역할 전부',                NULL, NOW(6), NOW(6), 0),
    ('00000083-0000-7000-8000-000000000008', NULL, 'scm-full',              'SCM 전체',             'SCM 전체 업무(세분 미지원)',            NULL, NOW(6), NOW(6), 0),
    ('00000083-0000-7000-8000-000000000009', NULL, 'erp-full',              'ERP 전체',             'ERP 전체 업무(세분 미지원)',            NULL, NOW(6), NOW(6), 0),
    ('00000083-0000-7000-8000-000000000010', NULL, 'finance-full',          'Finance 전체',         'Finance 전체 업무(세분 미지원)',        NULL, NOW(6), NOW(6), 0),
    ('00000083-0000-7000-8000-000000000011', NULL, 'ecommerce-full',        'E-commerce 전체',      'E-commerce 전체 업무(세분 미지원)',     NULL, NOW(6), NOW(6), 0),
    ('00000083-0000-7000-8000-000000000012', NULL, 'fan-full',              'fan 전체',             'fan 전체 업무(세분 미지원)',            NULL, NOW(6), NOW(6), 0),
    ('00000083-0000-7000-8000-000000000013', NULL, 'mes-full',              'MES 전체',             'MES 전체 업무(세분 미지원)',            NULL, NOW(6), NOW(6), 0),
    ('00000083-0000-7000-8000-000000000014', NULL, 'all-domains-full',      '모든 도메인 전체',     '카탈로그 역할 전부',                    NULL, NOW(6), NOW(6), 0),
    ('00000083-0000-7000-8000-000000000015', NULL, 'legacy-compat',         '이전 호환',            '이전 백필 전용(MIGRATION) — 카탈로그 역할 전부', NULL, NOW(6), NOW(6), 0);

INSERT IGNORE INTO domain_permission_set_role (set_id, domain_key, role_name)
SELECT s.id, v.domain_key, v.role_name
  FROM domain_permission_set s
  JOIN (
        SELECT 'wms-inventory-read'    AS system_key, 'wms' AS domain_key, 'INVENTORY_READ' AS role_name
        UNION ALL SELECT 'wms-inventory-operate', 'wms', 'INVENTORY_READ'
        UNION ALL SELECT 'wms-inventory-operate', 'wms', 'INVENTORY_WRITE'
        UNION ALL SELECT 'wms-inbound-operate',   'wms', 'INBOUND_READ'
        UNION ALL SELECT 'wms-inbound-operate',   'wms', 'INBOUND_WRITE'
        UNION ALL SELECT 'wms-outbound-operate',  'wms', 'OUTBOUND_READ'
        UNION ALL SELECT 'wms-outbound-operate',  'wms', 'OUTBOUND_WRITE'
        UNION ALL SELECT 'wms-master-read',       'wms', 'MASTER_READ'
        UNION ALL SELECT 'wms-ops-actions',       'wms', 'WMS_OPERATOR'
        UNION ALL SELECT 'scm-full',              'scm',       'SCM_OPERATOR'
        UNION ALL SELECT 'erp-full',              'erp',       'ERP_OPERATOR'
        UNION ALL SELECT 'finance-full',          'finance',   'FINANCE_OPERATOR'
        UNION ALL SELECT 'ecommerce-full',        'ecommerce', 'ECOMMERCE_OPERATOR'
        UNION ALL SELECT 'fan-full',              'fan',       'FAN_OPERATOR'
        UNION ALL SELECT 'mes-full',              'mes',       'MES_OPERATOR'
       ) v ON v.system_key = s.system_key;

-- wms-full · all-domains-full · legacy-compat — the catalog roles of the domain / of the whole catalog. Built from
-- the single-domain presets above so the three cannot drift from them (wms-full = the wms rows; the other two = all).
INSERT IGNORE INTO domain_permission_set_role (set_id, domain_key, role_name)
SELECT t.id, r.domain_key, r.role_name
  FROM domain_permission_set t
  JOIN domain_permission_set src ON src.system_key IN ('wms-inventory-operate', 'wms-inbound-operate',
                                                       'wms-outbound-operate', 'wms-master-read', 'wms-ops-actions')
  JOIN domain_permission_set_role r ON r.set_id = src.id
 WHERE t.system_key = 'wms-full';

INSERT IGNORE INTO domain_permission_set_role (set_id, domain_key, role_name)
SELECT t.id, r.domain_key, r.role_name
  FROM domain_permission_set t
  JOIN domain_permission_set src ON src.system_key IN ('wms-full', 'scm-full', 'erp-full', 'finance-full',
                                                       'ecommerce-full', 'fan-full', 'mes-full')
  JOIN domain_permission_set_role r ON r.set_id = src.id
 WHERE t.system_key IN ('all-domains-full', 'legacy-compat');

-- ========================================================================
-- 3. domain_access.manage → SUPER_ADMIN · TENANT_ADMIN (rbac.md Seed Matrix — inert / net-zero)
-- ========================================================================
INSERT IGNORE INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'domain_access.manage' FROM admin_roles WHERE name = 'SUPER_ADMIN';

INSERT IGNORE INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'domain_access.manage' FROM admin_roles WHERE name = 'TENANT_ADMIN';

-- ========================================================================
-- 4. Backfill (OD-1 · ADR-083 D8) — legacy-compat as MIGRATION
-- ========================================================================
-- (a) every membership of a non-'*' operator (a '*'-tenant membership row cannot exist for one — ADR-082 D2 —
--     and is excluded anyway: tenant '*' set rows belong to '*' operators only).
INSERT INTO operator_domain_set_grant
    (operator_id, tenant_id, set_id, source_key, group_origin, granted_by, granted_at)
SELECT a.operator_id, a.tenant_id, s.id, 'MIGRATION', NULL, NULL, NOW(6)
  FROM operator_tenant_assignment a
  JOIN admin_operators o ON o.id = a.operator_id
  JOIN domain_permission_set s ON s.system_key = 'legacy-compat'
 WHERE o.tenant_id <> '*'
   AND a.tenant_id <> '*'
   AND NOT EXISTS (
        SELECT 1
          FROM (SELECT operator_id, tenant_id, set_id, source_key FROM operator_domain_set_grant) g
         WHERE g.operator_id = a.operator_id
           AND g.tenant_id   = a.tenant_id
           AND g.set_id      = s.id
           AND g.source_key  = 'MIGRATION'
       );

-- (b) every '*' operator — one «all tenants» row (OD-3). No membership row is created (ADR-082 D2).
INSERT INTO operator_domain_set_grant
    (operator_id, tenant_id, set_id, source_key, group_origin, granted_by, granted_at)
SELECT o.id, '*', s.id, 'MIGRATION', NULL, NULL, NOW(6)
  FROM admin_operators o
  JOIN domain_permission_set s ON s.system_key = 'legacy-compat'
 WHERE o.tenant_id = '*'
   AND NOT EXISTS (
        SELECT 1
          FROM (SELECT operator_id, tenant_id, set_id, source_key FROM operator_domain_set_grant) g
         WHERE g.operator_id = o.id
           AND g.tenant_id   = '*'
           AND g.set_id      = s.id
           AND g.source_key  = 'MIGRATION'
       );
