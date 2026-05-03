-- TASK-MONO-027
-- Seeds the OAuth 2.0 clients used by ecommerce-microservices-platform's
-- web-store (B2C consumer) and admin-dashboard (operator) for the OIDC
-- authorization_code + PKCE flow. ecommerce becomes GAP's third tenant
-- consumer of the standard SAS token-issuance path (ADR-001 D1=A) after
-- wms (V0010) and fan-platform (V0011).
--
-- Why a Flyway seed and not the admin API:
--   TASK-BE-258 (admin-service OAuth client management API) is not yet ready.
--   We pre-register the clients here. When the admin API lands the rows
--   become administratable through the standard CRUD path — no schema change.
--
-- Secrets:
--   - The hash below is BCrypt(strength=10) of the literal string
--     "ecommerce-dev". Same dev-only fixture model as V0011's
--     "fan-platform-dev" — intended for portfolio / local dev environments.
--     Production deployments MUST rotate the secret via the admin API or by
--     overriding via env vars (ECOMMERCE_WEB_STORE_CLIENT_SECRET,
--     ECOMMERCE_ADMIN_DASHBOARD_CLIENT_SECRET) and updating these rows.
--   - Hash: $2a$10$M1zIY5Ieur41YpAmsfuy0u8UADvbaiVWcPiPJXnR1exRpgNCHW2rm
--     matches "ecommerce-dev" (verified via Spring Security
--     BCryptPasswordEncoder strength=10).
--
-- Tenant:
--   Both clients are scoped to tenant_id='ecommerce' (B2C). Cross-tenant
--   calls from these clients are rejected by TenantClaimValidator on the
--   ecommerce gateway. operator vs consumer distinction is carried in the
--   GAP token's account_type claim (CONSUMER | OPERATOR), enforced at the
--   ecommerce gateway by AccountTypeEnforcementFilter (TASK-BE-131).
--
-- Token TTLs (task spec, fan-platform V0011 pattern):
--   - access_token:        PT15M  = 900 seconds
--   - refresh_token:       PT24H  = 86400 seconds
--   - authorization_code:  PT5M   = 300 seconds

-- ============================================================
-- ecommerce-web-store-client
--   B2C consumer user-delegation flow (authorization_code + refresh_token + PKCE).
--   Used by web-store (Next.js) when an end-user signs in.
--   Confidential client (client_secret_basic) with PKCE enforced.
-- ============================================================
INSERT INTO oauth_clients (
    id,
    client_id,
    tenant_id,
    tenant_type,
    client_secret_hash,
    client_name,
    client_authentication_methods,
    authorization_grant_types,
    redirect_uris,
    scopes,
    client_settings,
    token_settings,
    created_at,
    updated_at
) VALUES (
    'ecommerce-web-store-client-id',
    'ecommerce-web-store-client',
    'ecommerce',
    'B2C',
    '$2a$10$M1zIY5Ieur41YpAmsfuy0u8UADvbaiVWcPiPJXnR1exRpgNCHW2rm',
    'ecommerce Web Store',
    '["client_secret_basic"]',
    '["authorization_code","refresh_token"]',
    '["http://localhost:3000/api/auth/callback/gap","http://web.ecommerce.local/api/auth/callback/gap"]',
    '["openid","profile","email","tenant.read","ecommerce.consumer"]',
    -- client_settings: include post-logout-redirect-uris inline. JSON_SET /
    -- JSON_ARRAY are MySQL-only and break the H2-backed SAS slice tests, so
    -- we embed the field directly to keep the migration portable across
    -- MySQL (production / Testcontainers) and H2 (slice tests).
    -- Same approach landed in V0011 (PR #144 fix).
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":true,"settings.client.require-authorization-consent":false,"settings.client.post-logout-redirect-uris":["http://localhost:3000/","http://web.ecommerce.local/"]}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":false,"settings.token.x509-certificate-bound-access-tokens":false,"settings.token.access-token-time-to-live":["java.time.Duration",900.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",86400.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000],"settings.token.device-code-time-to-live":["java.time.Duration",300.000000000]}',
    NOW(),
    NOW()
);

-- ============================================================
-- ecommerce-admin-dashboard-client
--   Operator user-delegation flow (authorization_code + refresh_token + PKCE).
--   Used by admin-dashboard (Next.js) when an OPERATOR signs in.
--   Same confidential + PKCE shape; differs only by redirect URIs and the
--   ecommerce.operator scope (vs ecommerce.consumer for web-store).
-- ============================================================
INSERT INTO oauth_clients (
    id,
    client_id,
    tenant_id,
    tenant_type,
    client_secret_hash,
    client_name,
    client_authentication_methods,
    authorization_grant_types,
    redirect_uris,
    scopes,
    client_settings,
    token_settings,
    created_at,
    updated_at
) VALUES (
    'ecommerce-admin-dashboard-client-id',
    'ecommerce-admin-dashboard-client',
    'ecommerce',
    'B2C',
    '$2a$10$M1zIY5Ieur41YpAmsfuy0u8UADvbaiVWcPiPJXnR1exRpgNCHW2rm',
    'ecommerce Admin Dashboard',
    '["client_secret_basic"]',
    '["authorization_code","refresh_token"]',
    '["http://localhost:3001/api/auth/callback/gap","http://admin.ecommerce.local/api/auth/callback/gap"]',
    '["openid","profile","email","tenant.read","ecommerce.operator"]',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":true,"settings.client.require-authorization-consent":false,"settings.client.post-logout-redirect-uris":["http://localhost:3001/","http://admin.ecommerce.local/"]}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":false,"settings.token.x509-certificate-bound-access-tokens":false,"settings.token.access-token-time-to-live":["java.time.Duration",900.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",86400.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000],"settings.token.device-code-time-to-live":["java.time.Duration",300.000000000]}',
    NOW(),
    NOW()
);

-- ============================================================
-- Tenant-scoped scopes for ecommerce.
-- web-store consumers carry ecommerce.consumer; admin-dashboard operators
-- carry ecommerce.operator. account_type enforcement at the ecommerce
-- gateway is the canonical authority — these scopes are advisory and let
-- audits / dashboards reason about access intent.
-- System scopes (openid/profile/email/offline_access/tenant.read) are
-- already seeded in V0008 (openid/profile/email/offline_access) and V0011
-- (tenant.read) — do not re-insert.
-- ============================================================
INSERT INTO oauth_scopes (scope_name, tenant_id, description, is_system, created_at) VALUES
    ('ecommerce.consumer', 'ecommerce', 'web-store consumer permissions (account_type=CONSUMER required)', FALSE, NOW()),
    ('ecommerce.operator', 'ecommerce', 'admin-dashboard operator permissions (account_type=OPERATOR required)', FALSE, NOW());
