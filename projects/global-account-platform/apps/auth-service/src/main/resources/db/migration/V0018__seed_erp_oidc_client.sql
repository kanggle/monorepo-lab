-- TASK-MONO-119 (ADR-MONO-016 ACCEPTED 2026-05-19, Option C)
-- Seeds the OAuth 2.0 client used by erp-platform's backend services for
-- service-to-service (client_credentials) calls. erp becomes GAP's next
-- tenant consumer of the standard SAS token-issuance path (ADR-001 D1=A)
-- after wms (V0010), fan-platform (V0011), ecommerce (V0012), scm (V0013),
-- finance (V0017).
--
-- v1 is backend-only — no PKCE user-flow client. The erp UI is rendered
-- by the unified platform console (ADR-MONO-013 §3.3); a user-flow client is
-- deferred to a follow-up migration if a dedicated flow is ever introduced.
--
-- Why a Flyway seed and not the admin API:
--   admin-service OAuth client management API is not yet ready. We pre-register
--   the client here. When the admin API lands the row becomes administratable
--   through the standard CRUD path — no schema change.
--
-- Secrets:
--   - The hash below is BCrypt(strength=10) of the literal string
--     "erp-dev". Same dev-only fixture model as V0011's
--     "fan-platform-dev" / V0012's "ecommerce-dev" / V0013's "scm-dev" /
--     V0017's "finance-dev" — intended for portfolio / local dev
--     environments. Production deployments MUST rotate the secret via the
--     admin API or by overriding via env var (OIDC_INTERNAL_CLIENT_SECRET)
--     and updating this row.
--   - The hash is FRESHLY generated for "erp-dev" and is intentionally
--     NOT V0013's "scm-dev" / V0017's "finance-dev" hash — reusing another
--     tenant's hash would mis-authenticate the erp client.
--   - Hash verified: $2a$10$1x0JEJjIUpm4JoYnLGvMPefzhC.D2ajgGlrUKQckk73b8O2bGrh0G
--     matches "erp-dev" (Spring Security BCryptPasswordEncoder strength=10).
--
-- Tenant:
--   The client is scoped to tenant_id='erp' (B2B enterprise, internal-system
--   — no external public traffic). Cross-tenant calls from this client are
--   rejected by TenantClaimValidator on the erp-platform gateway.
--
-- Token TTLs (V0013 scm-internal pattern):
--   - access_token:        PT30M  = 1800 seconds
--   - refresh_token:       PT720H = 2592000 seconds (unused for client_credentials)
--   - authorization_code:  PT5M   = 300 seconds (unused for client_credentials)

-- ============================================================
-- erp-platform-internal-services-client
--   Service-to-service flow (client_credentials).
--   Used by erp-platform backend services to call other monorepo
--   backends and by the platform console / external integration partners'
--   backends to call erp-platform endpoints.
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
    'erp-platform-internal-services-client-id',
    'erp-platform-internal-services-client',
    'erp',
    'B2B_ENTERPRISE',
    '$2a$10$1x0JEJjIUpm4JoYnLGvMPefzhC.D2ajgGlrUKQckk73b8O2bGrh0G',
    'erp-platform Internal Services',
    '["client_secret_basic"]',
    '["client_credentials"]',
    '[]',
    '["erp.read","erp.write"]',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":true,"settings.token.x509-certificate-bound-access-tokens":false,"settings.token.access-token-time-to-live":["java.time.Duration",1800.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",2592000.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000],"settings.token.device-code-time-to-live":["java.time.Duration",300.000000000]}',
    NOW(),
    NOW()
);

-- ============================================================
-- Tenant-scoped scopes for erp.
-- v1 starts with two coarse-grained scopes; the first service impl
-- (TASK-ERP-BE-001) may refine into per-bounded-context scopes
-- (erp.masterdata.read, erp.masterdata.write, erp.approval.write, etc.)
-- via a follow-up Flyway migration.
-- System scopes (openid/profile/email/offline_access) are owned by V0008
-- and tenant.read by V0011 — do not re-insert.
-- ============================================================
INSERT INTO oauth_scopes (scope_name, tenant_id, description, is_system, created_at) VALUES
    ('erp.read',  'erp', 'erp-platform read access (coarse, v1)',  FALSE, NOW()),
    ('erp.write', 'erp', 'erp-platform write access (coarse, v1)', FALSE, NOW());
