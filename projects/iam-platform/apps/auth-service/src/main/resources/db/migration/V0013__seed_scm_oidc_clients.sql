-- TASK-MONO-042
-- Seeds the OAuth 2.0 client used by scm-platform's backend services for
-- service-to-service (client_credentials) calls. scm becomes GAP's fourth
-- tenant consumer of the standard SAS token-issuance path (ADR-001 D1=A)
-- after wms (V0010), fan-platform (V0011), and ecommerce (V0012).
--
-- v1 is backend-only — no PKCE user-flow client. user-flow-client is deferred
-- to v2 when scm-platform-web (frontend) is introduced via a follow-up
-- migration.
--
-- Why a Flyway seed and not the admin API:
--   admin-service OAuth client management API is not yet ready. We pre-register
--   the client here. When the admin API lands the row becomes administratable
--   through the standard CRUD path — no schema change.
--
-- Secrets:
--   - The hash below is BCrypt(strength=10) of the literal string "scm-dev".
--     Same dev-only fixture model as V0011's "fan-platform-dev" and V0012's
--     "ecommerce-dev" — intended for portfolio / local dev environments.
--     Production deployments MUST rotate the secret via the admin API or by
--     overriding via env var (SCM_INTERNAL_SERVICES_CLIENT_SECRET) and
--     updating this row.
--   - Hash verified: $2a$10$Eck9mC32OSo1eicVmzvI/.T8ChCycZv8X6VB/HbCJxUvqFmVY5fim
--     matches "scm-dev" (Spring Security BCryptPasswordEncoder strength=10).
--
-- Tenant:
--   The client is scoped to tenant_id='scm' (B2B enterprise). Cross-tenant
--   calls from this client are rejected by TenantClaimValidator on the
--   scm-platform gateway.
--
-- Token TTLs (V0010 wms-internal pattern):
--   - access_token:        PT30M  = 1800 seconds
--   - refresh_token:       PT720H = 2592000 seconds (unused for client_credentials)
--   - authorization_code:  PT5M   = 300 seconds (unused for client_credentials)

-- ============================================================
-- scm-platform-internal-services-client
--   Service-to-service flow (client_credentials).
--   Used by scm-platform backend services to call other monorepo backends
--   (e.g., wms-platform inventory snapshot subscription) and by external
--   integration partners' backends to call scm-platform endpoints.
--   Confidential client (client_secret_basic).
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
    'scm-platform-internal-services-client-id',
    'scm-platform-internal-services-client',
    'scm',
    'B2B_ENTERPRISE',
    '$2a$10$Eck9mC32OSo1eicVmzvI/.T8ChCycZv8X6VB/HbCJxUvqFmVY5fim',
    'scm-platform Internal Services',
    '["client_secret_basic"]',
    '["client_credentials"]',
    '[]',
    '["scm.read","scm.write"]',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":true,"settings.token.x509-certificate-bound-access-tokens":false,"settings.token.access-token-time-to-live":["java.time.Duration",1800.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",2592000.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000],"settings.token.device-code-time-to-live":["java.time.Duration",300.000000000]}',
    NOW(),
    NOW()
);

-- ============================================================
-- Tenant-scoped scopes for scm.
-- v1 starts with two coarse-grained scopes; service skeleton (TASK-SCM-BE-001)
-- may refine into per-bounded-context scopes (scm.procurement.read,
-- scm.inventory.read, scm.settlement.write, etc.) via a follow-up Flyway
-- migration.
-- System scopes (openid/profile/email/offline_access) are owned by V0008
-- and tenant.read by V0011 — do not re-insert.
-- ============================================================
INSERT INTO oauth_scopes (scope_name, tenant_id, description, is_system, created_at) VALUES
    ('scm.read',  'scm', 'scm-platform read access (coarse, v1)',  FALSE, NOW()),
    ('scm.write', 'scm', 'scm-platform write access (coarse, v1)', FALSE, NOW());
