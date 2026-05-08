-- admin-service v1 seed data — dev / standalone profile only.
--
-- Authoritative reference:
--   specs/services/admin-service/domain-model.md § Reference Data Snapshot
--   specs/services/admin-service/architecture.md § Security § Roles
--   specs/services/admin-service/domain-model.md § 4 Seed Settings (v1)
--
-- Production deployments must run a separate manual procedure (or per-env
-- migration) to provision built-in roles + an initial admin user. V99 is
-- safe to skip in prod because Flyway numbers ascending and the prod
-- migration profile gates this file out via callback / location filter at
-- the platform level.
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
    );

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
);

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
);

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
    );
