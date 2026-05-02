-- TASK-BE-253
-- Seeds the two service-to-service OAuth clients used by community-service and
-- membership-service for the OIDC migration. Issued via client_credentials grant.
--
-- Why a Flyway seed and not the admin API:
--   TASK-BE-258 (admin-service OAuth client management API) is not yet ready,
--   so we pre-register the clients here. When the admin API lands, these rows
--   become administratable through the standard CRUD path — no schema change.
--
-- Secrets:
--   - The hash below is BCrypt(strength=10) of the literal string "secret".
--   - Production deployments MUST rotate the secret via the admin API or by
--     overriding via env vars (COMMUNITY_SERVICE_CLIENT_SECRET,
--     MEMBERSHIP_SERVICE_CLIENT_SECRET) and updating this row.
--   - Mapper prepends "{bcrypt}" automatically when constructing RegisteredClient,
--     so the stored hash here is the raw BCrypt output.
--   - Hash verified: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
--     matches "secret".
--
-- Tenant:
--   Both clients are scoped to tenant_id='fan-platform' (B2C). Cross-tenant calls
--   from these clients are rejected by TenantClaimValidator on the resource servers.

-- ============================================================
-- community-service-client
--   Used by community-service for outbound calls to:
--     - account-service (/internal/accounts/*)         requires scope: account.read
--     - membership-service (/internal/membership/*)    requires scope: membership.read
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
    'community-service-client-id',
    'community-service-client',
    'fan-platform',
    'B2C',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'Community Service Client',
    '["client_secret_basic"]',
    '["client_credentials"]',
    '[]',
    '["account.read","membership.read"]',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":true,"settings.token.x509-certificate-bound-access-tokens":false,"settings.token.access-token-time-to-live":["java.time.Duration",1800.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",2592000.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000],"settings.token.device-code-time-to-live":["java.time.Duration",300.000000000]}',
    NOW(),
    NOW()
);

-- ============================================================
-- membership-service-client
--   Reserved for future outbound calls from membership-service. Currently the
--   membership → account-service path still uses the legacy X-Internal-Token
--   header (out of scope for TASK-BE-253). Pre-registering the client here
--   allows the migration to proceed without an extra Flyway step.
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
    'membership-service-client-id',
    'membership-service-client',
    'fan-platform',
    'B2C',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'Membership Service Client',
    '["client_secret_basic"]',
    '["client_credentials"]',
    '[]',
    '["account.read","membership.read"]',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":true,"settings.token.x509-certificate-bound-access-tokens":false,"settings.token.access-token-time-to-live":["java.time.Duration",1800.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",2592000.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000],"settings.token.device-code-time-to-live":["java.time.Duration",300.000000000]}',
    NOW(),
    NOW()
);

-- ============================================================
-- Tenant-scoped scopes (account.read, membership.read).
-- System scopes (openid/profile/email/offline_access) were created in V0008.
-- Adding the resource scopes used by the seeded clients here.
-- ============================================================
INSERT INTO oauth_scopes (scope_name, tenant_id, description, is_system, created_at) VALUES
    ('account.read',    'fan-platform', 'Read access to account-service /internal/accounts/*',     FALSE, NOW()),
    ('membership.read', 'fan-platform', 'Read access to membership-service /internal/membership/*', FALSE, NOW());
