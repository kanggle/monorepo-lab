-- TASK-MONO-019
-- Seeds the OAuth 2.0 clients and tenant-scoped scopes used by wms-platform's
-- OIDC Resource Server migration. wms is GAP's first B2B enterprise consumer of
-- the standard SAS token-issuance path (ADR-001 D1=A).
--
-- Why a Flyway seed and not the admin API:
--   TASK-BE-258 (admin-service OAuth client management API) is not yet ready.
--   We pre-register the wms clients here. When the admin API lands the rows
--   become administratable through the standard CRUD path — no schema change.
--
-- Secrets:
--   - The hash below is BCrypt(strength=10) of the literal string "secret".
--     Same fixture as V0008/V0009 so test environments have deterministic
--     credentials. Production deployments MUST rotate the secret via the admin
--     API or by overriding via env vars (WMS_USER_FLOW_CLIENT_SECRET,
--     WMS_INTERNAL_SERVICES_CLIENT_SECRET) and updating these rows.
--   - Hash verified: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
--     matches "secret".
--
-- Tenant:
--   Both clients are scoped to tenant_id='wms' (B2B enterprise). Cross-tenant
--   calls from these clients are rejected by TenantClaimValidator on each wms
--   resource server.

-- ============================================================
-- wms-user-flow-client
--   B2B enterprise user-delegation flow (authorization_code + refresh_token).
--   Used by the wms web admin console (Next.js or equivalent) when a warehouse
--   operator signs in. Redirect URI placeholder until the wms web app declares
--   its real callback in TASK-MONO-020 follow-up.
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
    'wms-user-flow-client-id',
    'wms-user-flow-client',
    'wms',
    'B2B_ENTERPRISE',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'WMS User Flow Client',
    '["client_secret_basic"]',
    '["authorization_code","refresh_token"]',
    '["http://localhost:9001/callback"]',
    '["openid","profile","email","offline_access","wms.master.read","wms.master.write","wms.inventory.read","wms.inventory.write","wms.inbound.read","wms.inbound.write","wms.outbound.read","wms.outbound.write","wms.notification.write"]',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":true,"settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":false,"settings.token.x509-certificate-bound-access-tokens":false,"settings.token.access-token-time-to-live":["java.time.Duration",1800.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",2592000.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000],"settings.token.device-code-time-to-live":["java.time.Duration",300.000000000]}',
    NOW(),
    NOW()
);

-- ============================================================
-- wms-internal-services-client
--   Service-to-service client_credentials flow. Used by wms backend services
--   (inventory → master, outbound → inventory, etc.) when one wms service
--   needs to call another. Currently wms internal calls are Kafka-based —
--   pre-registering this client lets future REST s2s paths drop in without an
--   extra Flyway step (mirrors V0009's membership-service-client pattern).
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
    'wms-internal-services-client-id',
    'wms-internal-services-client',
    'wms',
    'B2B_ENTERPRISE',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'WMS Internal Services Client',
    '["client_secret_basic"]',
    '["client_credentials"]',
    '[]',
    '["wms.master.read","wms.master.write","wms.inventory.read","wms.inventory.write","wms.inbound.read","wms.inbound.write","wms.outbound.read","wms.outbound.write","wms.notification.write"]',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":true,"settings.token.x509-certificate-bound-access-tokens":false,"settings.token.access-token-time-to-live":["java.time.Duration",1800.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",2592000.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000],"settings.token.device-code-time-to-live":["java.time.Duration",300.000000000]}',
    NOW(),
    NOW()
);

-- ============================================================
-- Tenant-scoped scopes for wms.
-- Resource × action naming convention: <tenant>.<resource>.<action>.
-- See projects/wms-platform/specs/integration/gap-integration.md.
-- System scopes (openid/profile/email/offline_access) are owned by V0008.
-- ============================================================
INSERT INTO oauth_scopes (scope_name, tenant_id, description, is_system, created_at) VALUES
    ('wms.master.read',       'wms', 'Read access to master-service resources (warehouses, zones, locations, SKUs, lots, partners)', FALSE, NOW()),
    ('wms.master.write',      'wms', 'Write access to master-service resources',                                                       FALSE, NOW()),
    ('wms.inventory.read',    'wms', 'Read access to inventory-service resources (stock, reservations, transfers)',                    FALSE, NOW()),
    ('wms.inventory.write',   'wms', 'Write access to inventory-service resources (adjustments, reservations, mark-damaged)',          FALSE, NOW()),
    ('wms.inbound.read',      'wms', 'Read access to inbound-service resources (ASNs, inspections, putaway)',                          FALSE, NOW()),
    ('wms.inbound.write',     'wms', 'Write access to inbound-service resources (ASN creation, inspection recording, putaway confirm)', FALSE, NOW()),
    ('wms.outbound.read',     'wms', 'Read access to outbound-service resources (orders, pick lists, shipments)',                      FALSE, NOW()),
    ('wms.outbound.write',    'wms', 'Write access to outbound-service resources (order creation, picking, packing, shipping)',         FALSE, NOW()),
    ('wms.notification.write','wms', 'Write access to notification-service (publish operational alerts)',                              FALSE, NOW());
