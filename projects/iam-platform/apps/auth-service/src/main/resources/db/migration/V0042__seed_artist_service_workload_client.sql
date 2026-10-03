-- TASK-MONO-759 (owner decision 2026-10-03 — 갈래 A, IdP registration + permission-catalog
-- change explicitly approved) — register fan artist-service's workload client.
--
-- Who calls what
-- ------------------------------------------------------------------------------
--   artist-service-client — fan artist-service → ecommerce gateway → product-service
--                           GET /internal/sellers/{sellerId}
--
-- ADR-MONO-079 D2 requires the agency → store-seller link to be verified against the
-- store at write time (different database, no FK). artist-service had no IdP client at
-- all (resource server only), so every link answered 503 (TASK-MONO-748, fail-closed).
--
-- 🔴🔴 Never write Flyway placeholder syntax (a dollar sign followed by a braced name)
--    anywhere in this file, comments included — Flyway substitutes inside comments too
--    (the V0036 incident). Describe it; do not write it.
--
-- Scope
-- ------------------------------------------------------------------------------
-- `store.seller.read`, and nothing else. It is a MACHINE-ONLY scope: no end-user client is
-- registered for it, so a token carrying it is necessarily this workload. Two places gate
-- on it — the ecommerce gateway's AccountTypeEnforcementFilter (the one workload path,
-- /internal/sellers/**) and product-service's /internal/** decoder. 🔴 It is a READ scope
-- and the only surface it opens is one GET; it opens no seller write anywhere.
--
-- Roles
-- ------------------------------------------------------------------------------
-- None. `WorkloadRoleCatalog` lists this client with an EMPTY map ("measured, the answer
-- is none"). A role would leak this credential onto role-gated domain surfaces.
--
-- Tenant
-- ------------------------------------------------------------------------------
-- Registered to `fan-platform` / `B2C`, like community-service-client (V0009) — it is a
-- fan workload. It reaches the store tenant ONLY by the assume-tenant exchange
-- (ADR-MONO-076): the second statement below grants the exchange grant, and
-- `WorkloadTenantCatalog` enumerates `artist-service-client → {ecommerce}` — no other
-- tenant, no wildcard.
--
-- Why two statements instead of one INSERT with both grants
-- ------------------------------------------------------------------------------
-- `WorkloadTenantCatalogTest` attributes the exchange grant to a client by reading a
-- statement-scoped `client_id = '…'` predicate (the V0020 / V0037 shape). Keeping the
-- grant in an UPDATE of that shape keeps that population check exact, and mirrors the
-- V0036 → V0037 sequence the sibling workload client went through.
--
-- Secret
-- ------------------------------------------------------------------------------
-- client_secret_hash = BCrypt(strength=10) of the literal "secret" — the hash V0008 /
-- V0009 / V0019 / V0036 / V0038 share and `BcryptHashPinTest` pins. 🔴 Production must
-- rotate it via the caller-side `IAM_CLIENT_SECRET` (artist-service), as V0019 says.
--
-- Idempotency
-- ------------------------------------------------------------------------------
-- client_id is UNIQUE (uk_oauth_clients_client_id); `artist-service-client` appears in no
-- earlier seed (repo-wide grep, 2026-10-03). The UPDATE is guarded like V0037, and the
-- scope row is registered the way V0032 registered `artist.read` (system scope, NULL tenant,
-- WHERE NOT EXISTS).

INSERT INTO oauth_scopes (scope_name, tenant_id, description, is_system, created_at)
SELECT 'store.seller.read', NULL,
       'Machine scope: read one ecommerce store seller (id + status) via /internal/sellers/{id}',
       TRUE, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT scope_name, tenant_id FROM oauth_scopes) s
    WHERE s.scope_name = 'store.seller.read' AND s.tenant_id IS NULL
);

INSERT INTO oauth_clients (
    id, client_id, tenant_id, tenant_type, client_secret_hash, client_name,
    client_authentication_methods, authorization_grant_types, redirect_uris, scopes,
    client_settings, token_settings, created_at, updated_at
) VALUES (
    'artist-service-client-id',
    'artist-service-client',
    'fan-platform',
    'B2C',
    '$2a$10$0r6LHGsIgq6d5fkXCHwqQOHcuCA6ds8c8o9bSa25ucakM13V6VpsS',
    'Artist Service Workload Client',
    '["client_secret_basic"]',
    '["client_credentials"]',
    '[]',
    '["store.seller.read"]',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":true,"settings.token.x509-certificate-bound-access-tokens":false,"settings.token.access-token-time-to-live":["java.time.Duration",1800.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",2592000.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000],"settings.token.device-code-time-to-live":["java.time.Duration",300.000000000]}',
    NOW(),
    NOW()
);

UPDATE oauth_clients
SET authorization_grant_types = JSON_ARRAY_APPEND(
        authorization_grant_types,
        '$',
        'urn:ietf:params:oauth:grant-type:token-exchange')
WHERE client_id = 'artist-service-client'
  AND JSON_SEARCH(
        authorization_grant_types,
        'one',
        'urn:ietf:params:oauth:grant-type:token-exchange') IS NULL;
