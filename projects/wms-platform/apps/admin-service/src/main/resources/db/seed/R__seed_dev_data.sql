-- admin-service v1 seed data — built-in roles, bootstrap user, default settings.
--
-- Authoritative reference:
--   specs/services/admin-service/domain-model.md § Reference Data Snapshot
--   specs/services/admin-service/architecture.md § Security § Roles
--   specs/services/admin-service/domain-model.md § 4 Seed Settings (v1)
--
-- WHY THIS IS `R__` AND NOT `V99` (TASK-BE-585 — measured, not theoretical)
-- ---------------------------------------------------------------------------
-- This file used to be `V99__seed_dev_data.sql` in this same always-on
-- `db/migration` location. Because 99 sits far above the production timeline it
-- became the HIGHEST APPLIED version in every admin_db that ever booted. When
-- `ADR-MONO-065` later added `V3__order_shipment_tenant_axis.sql`, that new
-- migration resolved BELOW the applied 99 — an out-of-order migration, which
-- Flyway rejects by default:
--
--     Validate failed: Migrations have failed validation
--     Detected resolved migration not applied to database: 3.
--
-- admin-service then crash-looped on every host with an existing volume and all
-- eight `/api/v1/admin/dashboard/**` surfaces returned 500 (measured 2026-08-14,
-- TASK-BE-584 AC-0: restarts=12, gateway connect failure).
--
-- 🔴 A FRESH VOLUME IS PERMANENTLY GREEN ON THIS. V1·V2·V3·R__ all apply in
-- order, so CI, Testcontainers, and any reseeded demo cannot fail on it — the
-- defect lives in the history TABLE, not in this file's contents. Hence the fix
-- is the file's KIND (a repeatable carries no version, so it can never be out of
-- order and always runs after the versioned ones), not its number.
--
-- Same defect class as iam auth-service (TASK-MONO-524, 2026-08-11), which chose
-- `R__` for the same reason and documented the recovery procedure for databases
-- already carrying the watermark row:
--   projects/iam-platform/docs/flyway-dev-seed-migrations.md § 6
--
-- 🔴 EVERY STATEMENT HERE MUST STAY IDEMPOTENT (`ON CONFLICT DO NOTHING`).
-- A repeatable re-runs whenever its checksum changes, and on an existing
-- database it runs the moment this stops being V99 — straight into the rows V99
-- already inserted. Without the conflict clause that is a duplicate-key crash on
-- exactly the hosts this change exists to rescue.
--
-- 🔴 DO NOT RENAME THIS FILE once applied. Flyway tolerates a missing VERSIONED
-- migration (classified `future`) but NOT a missing repeatable — the description
-- is its identity, so a rename fails validation (TASK-MONO-524 § finding 2).
-- 🔵 TASK-BE-587 MOVED it (db/migration → db/seed) without renaming it, and that
-- distinction is the whole reason the move is safe: a repeatable's identity is
-- its description, and `script` is recorded relative to the location root, so
-- both stay `R__seed_dev_data.sql`. That is not an assumption — the two
-- `existingDatabase*` cells of DevSeedScopeIT reconstruct a database that applied
-- this file from the old location and then boot it from the new one, with and
-- without `db/seed` on the classpath.
--
-- 🔴 WHERE THIS FILE LIVES IS THE GATE (TASK-BE-587). `db/seed` is NOT on
-- `spring.flyway.locations` by default — `application.yml` names only
-- `classpath:db/migration`. Exactly two things open this location, and both are
-- non-production:
--
--     src/main/resources/application-dev.yml       (SPRING_PROFILES_ACTIVE=dev)
--     infra/demo/wms-devseed.override.yml          (the local demo stack)
--
-- so a production deployment applies not one statement of this file. That is the
-- arrangement the four sibling wms services have always used; admin-service was
-- the odd one out.
--
-- 🔴 WHY IT MOVED. Until 2026-08-15 this file sat in `db/migration`, which is
-- always on, while its own header claimed *"the prod migration profile gates
-- this file out via callback / location filter at the platform level"*. TASK-BE-587
-- measured that claim: admin-service had exactly one `application.yml`, zero
-- profile variants, and zero callbacks or location filters anywhere in the repo.
-- The gate did not exist — so this seed applied in EVERY environment, planting a
-- bootstrap `admin@wms.internal` account holding a global `WMS_SUPERADMIN`
-- assignment under a UUID this file itself publishes. Moving the file is what
-- makes the sentence above true instead of aspirational.
--
-- 🔵 AND REMOVING IT FROM PRODUCTION CLOSES NOTHING. The obvious objection —
-- "this is the only source of `admin_role`, so the eight dashboards die" — was
-- measured and is false. Authorisation here reads the JWT, never the table:
-- `SecurityConfig` builds authorities from the token's role claim plus the
-- `entitled_domains` → `ROLE_WMS_VIEWER` synthesis, and `@PreAuthorize` checks
-- those. `admin_role.permissions_json` is only ever read back out through the
-- role CRUD API; no authorisation decision consults it. The four tables here are
-- management data, not the security substrate.
--
-- The seed UUIDs are stable so dev tools / e2e fixtures can refer to them
-- without round-tripping through the DB. Built-in roles ALWAYS use these
-- ids regardless of environment to keep cross-environment scripts simple.

-- ---------------------------------------------------------------------------
-- Built-in roles (4) — is_builtin=true protects them from delete/deactivate.
-- Permission strings mirror admin-service-api.md § Authorization mapping.
-- ---------------------------------------------------------------------------
INSERT INTO admin_role (
    id, role_code, name, description, permissions_json, status, is_builtin,
    version, created_at, created_by, updated_at, updated_by
) VALUES
    (
        '11111111-1111-1111-1111-111111111111',
        'WMS_VIEWER',
        'Viewer',
        'Read-only — dashboards, query endpoints',
        '["INVENTORY_READ","INBOUND_READ","OUTBOUND_READ","MASTER_READ","ALERT_READ"]'::jsonb,
        'ACTIVE', TRUE, 0,
        now(), 'system:bootstrap', now(), 'system:bootstrap'
    ),
    (
        '22222222-2222-2222-2222-222222222222',
        'WMS_OPERATOR',
        'Operator',
        'Operational — read everywhere + write inventory / inbound / outbound',
        '["INVENTORY_READ","INVENTORY_WRITE","INBOUND_READ","INBOUND_WRITE","OUTBOUND_READ","OUTBOUND_WRITE","MASTER_READ","ALERT_READ","ALERT_ACKNOWLEDGE"]'::jsonb,
        'ACTIVE', TRUE, 0,
        now(), 'system:bootstrap', now(), 'system:bootstrap'
    ),
    (
        '33333333-3333-3333-3333-333333333333',
        'WMS_ADMIN',
        'Admin',
        'Read everywhere + admin-service write (user / role / settings)',
        '["INVENTORY_READ","INVENTORY_WRITE","INBOUND_READ","INBOUND_WRITE","OUTBOUND_READ","OUTBOUND_WRITE","MASTER_READ","MASTER_WRITE","ALERT_READ","ALERT_ACKNOWLEDGE","ADMIN_USER_WRITE","ADMIN_ROLE_WRITE","ADMIN_ASSIGNMENT_WRITE","ADMIN_SETTINGS_WRITE"]'::jsonb,
        'ACTIVE', TRUE, 0,
        now(), 'system:bootstrap', now(), 'system:bootstrap'
    ),
    (
        '44444444-4444-4444-4444-444444444444',
        'WMS_SUPERADMIN',
        'Super Admin',
        'Admin + force-deactivate, force-revoke, role bypass overrides',
        '["INVENTORY_READ","INVENTORY_WRITE","INBOUND_READ","INBOUND_WRITE","OUTBOUND_READ","OUTBOUND_WRITE","MASTER_READ","MASTER_WRITE","ALERT_READ","ALERT_ACKNOWLEDGE","ADMIN_USER_WRITE","ADMIN_ROLE_WRITE","ADMIN_ASSIGNMENT_WRITE","ADMIN_SETTINGS_WRITE","ADMIN_FORCE_OVERRIDE"]'::jsonb,
        'ACTIVE', TRUE, 0,
        now(), 'system:bootstrap', now(), 'system:bootstrap'
    )
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Seed user — admin@wms.internal as WMS_SUPERADMIN (global scope).
-- ---------------------------------------------------------------------------
INSERT INTO admin_user (
    id, user_code, email, name, phone, status, default_warehouse_id,
    version, created_at, created_by, updated_at, updated_by
) VALUES (
    '55555555-5555-5555-5555-555555555555',
    'USR-0001',
    'admin@wms.internal',
    'Bootstrap Admin',
    NULL,
    'ACTIVE',
    NULL,
    0, now(), 'system:bootstrap', now(), 'system:bootstrap'
)
ON CONFLICT DO NOTHING;

INSERT INTO admin_user_role_assignment (
    id, user_id, role_id, warehouse_id, granted_at, granted_by,
    revoked_at, revoked_by, status, version, created_at, updated_at
) VALUES (
    '66666666-6666-6666-6666-666666666666',
    '55555555-5555-5555-5555-555555555555',
    '44444444-4444-4444-4444-444444444444',
    NULL,
    now(), 'system:bootstrap',
    NULL, NULL,
    'ACTIVE', 0, now(), now()
)
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Default settings (4 — domain-model.md § 4).
-- Each carries a JSON Schema draft-07 fragment that constrains value_json.
-- ---------------------------------------------------------------------------
-- GLOBAL settings carry the sentinel UUID 00000000-... in warehouse_id so the
-- composite PK works without NULL handling. WAREHOUSE-scoped settings would
-- carry the real warehouse id.
INSERT INTO admin_setting (
    key, warehouse_id, scope, value_json, schema_json, description,
    version, created_at, created_by, updated_at, updated_by
) VALUES
    (
        'inventory.reservation.ttl_hours',
        '00000000-0000-0000-0000-000000000000'::uuid,
        'GLOBAL',
        '24'::jsonb,
        '{"type":"integer","minimum":1,"maximum":168}'::jsonb,
        'Reservation TTL in hours',
        0, now(), 'system:bootstrap', now(), 'system:bootstrap'
    ),
    (
        'inventory.low_stock.default_threshold_qty',
        '00000000-0000-0000-0000-000000000000'::uuid,
        'GLOBAL',
        '10'::jsonb,
        '{"type":"integer","minimum":0,"maximum":100000}'::jsonb,
        'Default low-stock threshold quantity',
        0, now(), 'system:bootstrap', now(), 'system:bootstrap'
    ),
    (
        'inbound.asn.auto_close_delay_hours',
        '00000000-0000-0000-0000-000000000000'::uuid,
        'GLOBAL',
        '48'::jsonb,
        '{"type":"integer","minimum":1,"maximum":336}'::jsonb,
        'Hours after received before ASN auto-closes',
        0, now(), 'system:bootstrap', now(), 'system:bootstrap'
    ),
    (
        'outbound.saga.sweeper_interval_seconds',
        '00000000-0000-0000-0000-000000000000'::uuid,
        'GLOBAL',
        '60'::jsonb,
        '{"type":"integer","minimum":5,"maximum":3600}'::jsonb,
        'Outbound saga sweeper tick interval (seconds)',
        0, now(), 'system:bootstrap', now(), 'system:bootstrap'
    )
ON CONFLICT DO NOTHING;
