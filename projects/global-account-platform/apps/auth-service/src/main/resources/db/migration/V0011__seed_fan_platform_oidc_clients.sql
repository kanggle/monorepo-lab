-- TASK-MONO-026
-- Seeds the OAuth 2.0 client used by fan-platform-web (Next.js) for the OIDC
-- authorization_code + PKCE flow. fan-platform is GAP's primary B2C consumer.
--
-- Why a Flyway seed and not the admin API:
--   TASK-BE-258 (admin-service OAuth client management API) is not yet ready.
--   We pre-register the client here. When the admin API lands the row
--   becomes administratable through the standard CRUD path — no schema change.
--
-- Secrets:
--   - The hash below is BCrypt(strength=10) of the literal string "fan-platform-dev".
--     Intended for dev/portfolio environments only.
--     Production deployments MUST rotate the secret via the admin API or by
--     overriding via env vars (FAN_PLATFORM_USER_FLOW_CLIENT_SECRET) and
--     updating this row.
--   - Hash: $2a$10$8K1p/a0dR1xqM8LjelOS.OEI7NJJkMvNKbFbMaWkVWzBJUY9qQ4hO
--     matches "fan-platform-dev" (BCryptPasswordEncoder strength=10).
--
-- Tenant:
--   Client is scoped to tenant_id='fan-platform' (B2C self-service). Cross-tenant
--   calls from this client are rejected by TenantClaimValidator on each
--   fan-platform resource server.
--
-- Token TTLs (task spec):
--   - access_token:  PT15M  = 900 seconds
--   - refresh_token: PT24H  = 86400 seconds

-- ============================================================
-- fan-platform-user-flow-client
--   B2C user-delegation flow (authorization_code + refresh_token + PKCE).
--   Used by fan-platform-web (Next.js) when a fan signs in.
--   Confidential client (client_secret_basic) with PKCE enforced —
--   refresh_token grant requires client authentication.
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
    'fan-platform-user-flow-client-id',
    'fan-platform-user-flow-client',
    'fan-platform',
    'B2C',
    '$2a$10$8K1p/a0dR1xqM8LjelOS.OEI7NJJkMvNKbFbMaWkVWzBJUY9qQ4hO',
    'fan-platform Web',
    '["client_secret_basic"]',
    '["authorization_code","refresh_token"]',
    '["http://localhost:3000/api/auth/callback/gap","http://fan-platform.local/api/auth/callback/gap"]',
    '["openid","profile","email","tenant.read"]',
    -- client_settings: include post-logout-redirect-uris inline (was a separate
    -- JSON_SET UPDATE in the original spec, but JSON_SET / JSON_ARRAY are
    -- MySQL-only and break the H2-backed SAS slice tests; embedding the
    -- field directly keeps the migration portable across MySQL (production
    -- and Testcontainers) and H2 (slice tests).
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":true,"settings.client.require-authorization-consent":false,"settings.client.post-logout-redirect-uris":["http://localhost:3000/","http://fan-platform.local/"]}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":false,"settings.token.x509-certificate-bound-access-tokens":false,"settings.token.access-token-time-to-live":["java.time.Duration",900.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",86400.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000],"settings.token.device-code-time-to-live":["java.time.Duration",300.000000000]}',
    NOW(),
    NOW()
);

-- ============================================================
-- Tenant-scoped scope: tenant.read
-- Allows the client to read tenant context claims from the token.
-- System scope (tenant_id=NULL) so it is available across all tenants.
-- V0008 seeds openid/profile/email/offline_access system scopes.
-- ============================================================
INSERT INTO oauth_scopes (scope_name, tenant_id, description, is_system, created_at) VALUES
    ('tenant.read', NULL, 'Read access to tenant_id and tenant_type claims in the issued token', TRUE, NOW());

-- ============================================================
-- fan-platform-internal-services-client
-- DEFERRED to v2 (TASK-MONO-026 Out of Scope).
-- fan-platform v1 service-to-service calls use internal Spring Security
-- context delegation (no OIDC token exchange). The client_credentials
-- client will be added when membership-service / notification-service
-- REST paths require it in v2.
-- ============================================================
