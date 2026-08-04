-- TASK-BE-570
-- fan-platform-user-flow-client OAuth scope registration gap.
--
-- fan-platform-web's NextAuth provider (web/fan-platform-web/src/shared/auth/auth.ts)
-- requests:
--     openid profile email offline_access fan-platform.community.read
--     fan-platform.community.write fan-platform.artist.read
--
-- V0011 registered the client with only ["openid","profile","email","tenant.read"].
-- Spring Authorization Server rejects any `scope` parameter containing a value
-- outside the client's registered allow-list with `invalid_scope` at the
-- /oauth2/authorize step, before the login form is even shown — every browser
-- login attempt against fan-platform-user-flow-client fails. This is a
-- seed-migration gap: fan-platform's own contract
-- (projects/fan-platform/specs/integration/iam-integration.md § Scopes) already
-- documents this scope set as intended.
--
-- Fix (strictly additive, V0011 is never edited — checksum-locked historical
-- record):
--   1. Register three new tenant-scoped catalog rows in oauth_scopes
--      (tenant_id='fan-platform', is_system=false), following the
--      V0010/V0012/V0013 resource-scope pattern.
--      offline_access is NOT re-registered here — it is already a global
--      system scope (tenant_id=NULL) seeded by V0008.
--   2. Append exactly the four scopes auth.ts requests
--      (offline_access, fan-platform.community.read,
--      fan-platform.community.write, fan-platform.artist.read) to
--      fan-platform-user-flow-client's oauth_clients.scopes column via
--      JSON_ARRAY_APPEND, guarded by a JSON_SEARCH idempotency check per
--      value (mirrors V0023's single-scope append pattern).
--
-- Out of scope (see task Out of Scope / Failure Scenarios F2/F3):
--   - fan-platform.artist.write, fan-platform.membership.read/write,
--     fan-platform.notification.write — not currently requested by auth.ts.
--   - Resource-server enforcement of these scopes in community-service /
--     artist-service — a separate authorization-model decision, not bundled
--     here. Making the scopes *requestable* is independent from making them
--     *enforced*.
--
-- Portability: JSON_ARRAY_APPEND / JSON_SEARCH are MySQL-only, consistent with
-- V0020/V0023 precedent — Flyway runs only against MySQL 8.0 in this service;
-- the sole H2-backed test (OAuth2AuthorizationServerSliceTest) disables Flyway
-- (spring.flyway.enabled=false) and builds its schema via Hibernate ddl-auto,
-- so no migration ever executes on H2.

-- ============================================================
-- Tenant-scoped scopes for fan-platform.
-- Resource × action naming convention: <tenant>.<resource>.<action>.
-- See projects/fan-platform/specs/integration/iam-integration.md § Scopes.
-- System scopes (openid/profile/email/offline_access) are owned by V0008.
-- tenant.read (fan-platform-scoped system-style grant) is owned by V0011.
-- ============================================================
INSERT INTO oauth_scopes (scope_name, tenant_id, description, is_system, created_at) VALUES
    ('fan-platform.community.read',  'fan-platform', 'Read access to community-service resources (posts, comments)',  FALSE, NOW()),
    ('fan-platform.community.write', 'fan-platform', 'Write access to community-service resources (posts, comments)', FALSE, NOW()),
    ('fan-platform.artist.read',     'fan-platform', 'Read access to artist-service resources (artist profiles)',     FALSE, NOW());

-- ============================================================
-- fan-platform-user-flow-client: append the four scopes auth.ts requests.
-- One JSON_ARRAY_APPEND per value, each individually idempotency-guarded via
-- JSON_SEARCH so a re-run (or a partially-applied prior run) is a no-op.
-- ============================================================
UPDATE oauth_clients
SET scopes = JSON_ARRAY_APPEND(scopes, '$', 'offline_access')
WHERE client_id = 'fan-platform-user-flow-client'
  AND JSON_SEARCH(scopes, 'one', 'offline_access') IS NULL;

UPDATE oauth_clients
SET scopes = JSON_ARRAY_APPEND(scopes, '$', 'fan-platform.community.read')
WHERE client_id = 'fan-platform-user-flow-client'
  AND JSON_SEARCH(scopes, 'one', 'fan-platform.community.read') IS NULL;

UPDATE oauth_clients
SET scopes = JSON_ARRAY_APPEND(scopes, '$', 'fan-platform.community.write')
WHERE client_id = 'fan-platform-user-flow-client'
  AND JSON_SEARCH(scopes, 'one', 'fan-platform.community.write') IS NULL;

UPDATE oauth_clients
SET scopes = JSON_ARRAY_APPEND(scopes, '$', 'fan-platform.artist.read')
WHERE client_id = 'fan-platform-user-flow-client'
  AND JSON_SEARCH(scopes, 'one', 'fan-platform.artist.read') IS NULL;
