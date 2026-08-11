-- TASK-FAN-BE-045 AC-6 (fan-platform), ADR-004 (ACCEPTED — A), ADR-MONO-005.
--
-- Grant community-service-client the machine scope for artist-service's new
-- /internal/artists/exists surface.
--
-- Why this row is load-bearing. community-service now validates a follow target
-- against artist-service synchronously and FAIL-CLOSED. Its workload token is
-- minted for community-service-client; artist-service's Order(1) internal chain
-- grants ROLE_INTERNAL only to a token carrying the `artist.read` scope. Without
-- this grant, Spring Authorization Server rejects the client_credentials request
-- with `invalid_scope`, the checker fails closed, and EVERY follow is refused —
-- a fail-closed outage that looks exactly like a working security control. It
-- ships in the same PR as the check for that reason (CLAUDE.md § Cross-Project
-- Changes: staggered PRs create transiently broken main).
--
-- Why `artist.read` and NOT `fan-platform.artist.read`. These are two different
-- scopes in two different families, and the distinction is the whole security
-- property of the internal surface:
--   * `fan-platform.artist.read` (V0030) is an END-USER resource scope, held by
--     fan-platform-user-flow-client and requested on every fan's browser login.
--     If the internal chain keyed on it, every logged-in fan would satisfy the
--     workload discriminator and could call /internal/** directly.
--   * `artist.read` joins the MACHINE scope family (`account.read`,
--     `membership.read` — V0009) that is only ever granted to
--     client_credentials clients. No end-user token carries it.
-- Naming follows that machine family deliberately, not the tenant-prefixed
-- resource-scope convention, because family membership is what makes the
-- discriminator positive and machine-only.
--
-- Strictly additive: V0009 is never edited (checksum-locked historical record).
-- Same JSON_ARRAY_APPEND + JSON_SEARCH idempotency-guard pattern as V0030/V0023.
-- Portability note per V0030: Flyway runs only against MySQL 8.0 for this
-- service; the sole H2-backed slice test disables Flyway.

-- ============================================================
-- Catalog row. is_system=TRUE with tenant_id=NULL, matching the machine-scope
-- family: account.read / membership.read are not tenant-prefixed, and this
-- scope authorizes a service-to-service surface rather than a tenant's user.
-- ============================================================
INSERT INTO oauth_scopes (scope_name, tenant_id, description, is_system, created_at)
SELECT 'artist.read', NULL,
       'Machine scope: read artist-service internal resources (artist-account existence)',
       TRUE, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT scope_name, tenant_id FROM oauth_scopes) s
    WHERE s.scope_name = 'artist.read' AND s.tenant_id IS NULL
);

-- ============================================================
-- community-service-client: append `artist.read` to its existing
-- ["account.read","membership.read"]. Idempotency-guarded per value.
-- membership-service-client is deliberately NOT granted this scope — it makes no
-- outbound call to artist-service, and a scope nobody uses is a standing grant
-- nobody audits.
-- ============================================================
UPDATE oauth_clients
SET scopes = JSON_ARRAY_APPEND(scopes, '$', 'artist.read')
WHERE client_id = 'community-service-client'
  AND JSON_SEARCH(scopes, 'one', 'artist.read') IS NULL;
