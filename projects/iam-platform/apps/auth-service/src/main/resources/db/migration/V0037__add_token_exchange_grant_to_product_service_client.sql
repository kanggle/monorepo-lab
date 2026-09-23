-- TASK-MONO-721 (ADR-MONO-076, ACCEPTED 2026-09-23 — 갈래 D) D3
--
-- Register the RFC 8693 token-exchange grant on `product-service-client` so a
-- workload credential can obtain a token for the tenant it must act on, instead
-- of being refused because the tenant it is registered to is not the tenant in
-- the request path.
--
-- Mirrors V0020 exactly (same append, same idempotency guard). The grant is
-- per client and stays that way: a client without this row reaches no exchange,
-- and WorkloadTenantCatalog decides which tenants the ones that do may assume.
--
-- ---------------------------------------------------------------------------
-- 🔴 CORRECTION to V0019's header — read this before quoting that file
-- ---------------------------------------------------------------------------
-- V0019__seed_internal_service_workload_clients.sql says, about these same
-- workload credentials:
--
--     "The receiving resource servers (account/security) validate signature +
--      issuer only and do NOT pin tenant (they serve all tenants), so the
--      tenant claim is informational here."
--
-- That sentence is FALSE, and it was false before this migration existed.
-- Measured 2026-09-23 (TASK-MONO-672 item 12, demo window):
--
--     gateway-service JwtAuthenticationFilter  injects X-Tenant-Id from the
--                                              VERIFIED token's tenant_id
--     account-service TenantScopeGuard         rejects when that header does
--                                              not equal the path {tenantId}
--     result                                   403 TENANT_SCOPE_DENIED, always
--
-- So the receiving side DOES pin tenant for any call that arrives through the
-- gateway, and the tenant claim is not informational — it is the authorization
-- decision (jwt-standard-claims.md § JWT Validation rule 6).
--
-- 🔴 TASK-MONO-717 quoted that stale sentence to choose this credential's
--    tenant, and that choice was half of why its provisioning call failed. The
--    correction lives HERE, in a new file, and NOT as an edit to V0019 —
--    V0019 is an APPLIED migration and Flyway's checksum covers the whole file
--    INCLUDING COMMENTS, so editing it kills startup for every database that
--    already has its history row. auth-service does not disable
--    validateOnMigrate. CI would never catch it: CI always starts on an empty
--    volume (projects/iam-platform/docs/flyway-dev-seed-migrations.md § 1-2 is
--    the incident that taught this repo the lesson the hard way).
--    The byte-level fix to V0019 is gated on a fresh volume: TASK-MONO-722.
-- ---------------------------------------------------------------------------
--
-- 🔴 Never write Flyway placeholder syntax -- a dollar sign followed by a braced
--    name -- anywhere in a migration, COMMENTS INCLUDED. Flyway substitutes
--    placeholders inside comments too, and an unresolvable one fails the migration
--    at context load, which reads as "every repository slice test is broken"
--    rather than as a comment problem (measured on V0036, TASK-MONO-717).
--
-- 🔴🔴 This paragraph's FIRST version was itself the violation: it spelled the
--    sequence out in the act of forbidding it, and CI went red on exactly the
--    failure it was warning about. Describe the syntax; do not write it.
--    (The repo has a name for this: a discriminator that matches its own
--    documentation.)

UPDATE oauth_clients
SET authorization_grant_types = JSON_ARRAY_APPEND(
        authorization_grant_types,
        '$',
        'urn:ietf:params:oauth:grant-type:token-exchange')
WHERE client_id = 'product-service-client'
  AND JSON_SEARCH(
        authorization_grant_types,
        'one',
        'urn:ietf:params:oauth:grant-type:token-exchange') IS NULL;
