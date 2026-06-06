-- TASK-MONO-180
-- Forward migration for the residual `gap` → `iam` rename (follow-up to
-- TASK-MONO-179, which renamed the project/alias/iss but deliberately left the
-- persisted wire values). The seeding migrations V0011/V0012/V0015 are
-- checksum-locked historical record and MUST NOT be edited — the value changes
-- they encoded land here as a new forward UPDATE migration.
--
-- Two changes, both idempotent (re-running is a no-op once applied):
--
--   1. platform-console-web reserved tenant_id 'gap' → 'iam'
--      The console's own operational tenant slug (V0015). `iam` is added to the
--      admin-service reserved-word set (CreateTenantUseCase / multi-tenancy.md)
--      in the same PR so no consumer can register it. `gap` stays reserved too
--      (historical safety — a pre-migration token may still carry tenant_id='gap'
--      until redeploy). The RegisteredClientRepository maps this column onto the
--      `custom.tenant_id` client setting the BFF/seed ITs assert.
--
--   2. Consumer OIDC redirect_uris `/api/auth/callback/gap` → `/api/auth/callback/iam`
--      The NextAuth provider id flips `gap` → `iam` in the fan + ecommerce
--      frontends (auth.ts `id`), which changes the callback path NextAuth posts
--      to. The registered redirect_uris MUST match or every consumer login fails
--      with redirect_uri_mismatch. Clients: fan-platform-user-flow-client (V0011),
--      ecommerce-web-store-client + ecommerce-admin-dashboard-client (V0012).
--      post_logout_redirect_uris are app-root landings (no /callback) — untouched.

-- 1. Console operational tenant slug.
UPDATE oauth_clients
   SET tenant_id  = 'iam',
       updated_at = NOW()
 WHERE client_id = 'platform-console-web'
   AND tenant_id = 'gap';

-- 2. Consumer OIDC callback redirect_uris.
UPDATE oauth_clients
   SET redirect_uris = REPLACE(redirect_uris, '/api/auth/callback/gap', '/api/auth/callback/iam'),
       updated_at    = NOW()
 WHERE client_id IN (
        'fan-platform-user-flow-client',
        'ecommerce-web-store-client',
        'ecommerce-admin-dashboard-client'
   )
   AND redirect_uris LIKE '%/api/auth/callback/gap%';
