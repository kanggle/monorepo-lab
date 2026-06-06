-- TASK-BE-317 (ADR-005 옵션 A, 무중단 단계 1)
-- Seeds client_credentials registered clients for GAP-internal service-to-service
-- (workload) authentication. Each GAP-internal caller obtains a short-lived JWT via
-- the client_credentials grant and presents it as `Authorization: Bearer` on
-- /internal/** calls. The receiving side (account/security) verifies the JWT via JWKS
-- while still accepting the legacy X-Internal-Token during the dual-allow window
-- (ADR-005 단계 2). The caller-side switch to Bearer is TASK-BE-318 (단계 3).
--
-- Why these four clients:
--   admin-service-client    — admin-service  → account/auth/security /internal/**
--   auth-service-client     — auth-service   → account /internal/** (+ social)
--   security-service-client — security-service → account /internal/**
--   account-service-client  — account-service → auth /internal/** (credential)
--   community/membership callers already have clients (V0009) → reused, not re-seeded.
--
-- Tenant:
--   These clients are GAP platform infrastructure, not bound to a product tenant.
--   tenant_id='global-account-platform', tenant_type='INTERNAL'. The receiving
--   resource servers (account/security) validate signature + issuer only and do NOT
--   pin tenant (they serve all tenants), so the tenant claim is informational here.
--   TenantClaimTokenCustomizer (auth-service) requires a non-blank tenant_id/tenant_type
--   at issuance — these values satisfy that fail-closed guard.
--
-- Secret:
--   client_secret_hash = BCrypt(strength=10) of the literal "secret"
--   ($2a$10$0r6LHGsIgq6d5fkXCHwqQOHcuCA6ds8c8o9bSa25ucakM13V6VpsS — the same pinned
--   hash verified by BcryptHashPinTest, reused across V0008/V0009). Mapper prepends
--   "{bcrypt}". Production MUST rotate via env override
--   (<SERVICE>_SERVICE_CLIENT_SECRET) when callers switch in TASK-BE-318.
--
-- Idempotency:
--   client_id is UNIQUE (uk_oauth_clients_client_id). The svc client_ids below do not
--   collide with existing seeds (test-internal-client / *-service-client / *-internal-services-client).

-- ============================================================
-- internal.invoke — system scope (tenant_id NULL, shared across all tenants).
-- Granted to the GAP-internal workload clients. Receiving side does not enforce it
-- in TASK-BE-317 (any validly-signed GAP JWT passes); pre-registering keeps the
-- scope catalog complete and allows scope-based hardening in TASK-BE-319.
-- ============================================================
INSERT INTO oauth_scopes (scope_name, tenant_id, description, is_system, created_at) VALUES
    ('internal.invoke', NULL, 'Service-to-service workload identity scope for GAP-internal /internal/** calls', TRUE, NOW());

-- ============================================================
-- admin-service-client
-- ============================================================
INSERT INTO oauth_clients (
    id, client_id, tenant_id, tenant_type, client_secret_hash, client_name,
    client_authentication_methods, authorization_grant_types, redirect_uris, scopes,
    client_settings, token_settings, created_at, updated_at
) VALUES (
    'admin-service-client-id',
    'admin-service-client',
    'global-account-platform',
    'INTERNAL',
    '$2a$10$0r6LHGsIgq6d5fkXCHwqQOHcuCA6ds8c8o9bSa25ucakM13V6VpsS',
    'Admin Service Workload Client',
    '["client_secret_basic"]',
    '["client_credentials"]',
    '[]',
    '["internal.invoke"]',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":true,"settings.token.x509-certificate-bound-access-tokens":false,"settings.token.access-token-time-to-live":["java.time.Duration",1800.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",2592000.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000],"settings.token.device-code-time-to-live":["java.time.Duration",300.000000000]}',
    NOW(),
    NOW()
);

-- ============================================================
-- auth-service-client
-- ============================================================
INSERT INTO oauth_clients (
    id, client_id, tenant_id, tenant_type, client_secret_hash, client_name,
    client_authentication_methods, authorization_grant_types, redirect_uris, scopes,
    client_settings, token_settings, created_at, updated_at
) VALUES (
    'auth-service-client-id',
    'auth-service-client',
    'global-account-platform',
    'INTERNAL',
    '$2a$10$0r6LHGsIgq6d5fkXCHwqQOHcuCA6ds8c8o9bSa25ucakM13V6VpsS',
    'Auth Service Workload Client',
    '["client_secret_basic"]',
    '["client_credentials"]',
    '[]',
    '["internal.invoke"]',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":true,"settings.token.x509-certificate-bound-access-tokens":false,"settings.token.access-token-time-to-live":["java.time.Duration",1800.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",2592000.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000],"settings.token.device-code-time-to-live":["java.time.Duration",300.000000000]}',
    NOW(),
    NOW()
);

-- ============================================================
-- security-service-client
-- ============================================================
INSERT INTO oauth_clients (
    id, client_id, tenant_id, tenant_type, client_secret_hash, client_name,
    client_authentication_methods, authorization_grant_types, redirect_uris, scopes,
    client_settings, token_settings, created_at, updated_at
) VALUES (
    'security-service-client-id',
    'security-service-client',
    'global-account-platform',
    'INTERNAL',
    '$2a$10$0r6LHGsIgq6d5fkXCHwqQOHcuCA6ds8c8o9bSa25ucakM13V6VpsS',
    'Security Service Workload Client',
    '["client_secret_basic"]',
    '["client_credentials"]',
    '[]',
    '["internal.invoke"]',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":true,"settings.token.x509-certificate-bound-access-tokens":false,"settings.token.access-token-time-to-live":["java.time.Duration",1800.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",2592000.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000],"settings.token.device-code-time-to-live":["java.time.Duration",300.000000000]}',
    NOW(),
    NOW()
);

-- ============================================================
-- account-service-client
-- ============================================================
INSERT INTO oauth_clients (
    id, client_id, tenant_id, tenant_type, client_secret_hash, client_name,
    client_authentication_methods, authorization_grant_types, redirect_uris, scopes,
    client_settings, token_settings, created_at, updated_at
) VALUES (
    'account-service-client-id',
    'account-service-client',
    'global-account-platform',
    'INTERNAL',
    '$2a$10$0r6LHGsIgq6d5fkXCHwqQOHcuCA6ds8c8o9bSa25ucakM13V6VpsS',
    'Account Service Workload Client',
    '["client_secret_basic"]',
    '["client_credentials"]',
    '[]',
    '["internal.invoke"]',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":true,"settings.token.x509-certificate-bound-access-tokens":false,"settings.token.access-token-time-to-live":["java.time.Duration",1800.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",2592000.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000],"settings.token.device-code-time-to-live":["java.time.Duration",300.000000000]}',
    NOW(),
    NOW()
);
