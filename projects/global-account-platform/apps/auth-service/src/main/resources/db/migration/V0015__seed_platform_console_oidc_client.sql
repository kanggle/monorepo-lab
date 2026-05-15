-- TASK-BE-296
-- Seeds the OAuth 2.0 PUBLIC client used by platform-console-web for the OIDC
-- authorization_code + PKCE flow. platform-console is GAP's federated operator
-- console (ADR-MONO-013 D5). One operator login covers all federated domains
-- (SSO); the console holds tokens in HttpOnly cookies and refreshes via a
-- server route.
--
-- Public-client lineage (ADR-003):
--   This row mirrors the demo-spa-client public-client pattern seeded in V0008:
--     client_secret_hash            = NULL          (NO client secret — PKCE only)
--     client_authentication_methods = ["none"]      (public client)
--     authorization_grant_types     = authorization_code + refresh_token
--     settings.client.require-proof-key = true       (PKCE mandatory)
--     settings.token.reuse-refresh-tokens = false    (rotation on every refresh)
--   The PublicClientRefreshTokenAuthenticationConverter +
--   PublicClientRevokeAuthenticationConverter +
--   SasRefreshTokenAuthenticationProvider path (ADR-003 옵션 B closure,
--   TASK-BE-272/274) authenticates this client's refresh_token / revoke grants
--   exactly as it does demo-spa-client — no new wiring is required because the
--   converter fires for ANY registered client whose
--   client_authentication_methods contains NONE.
--
-- Why a Flyway seed and not the admin API:
--   The admin-service OAuth client management API (TASK-BE-258) is not yet
--   ready. We pre-register the client here. When the admin API lands the row
--   becomes administratable through the standard CRUD path — no schema change.
--
-- Tenant:
--   The console is an operator-facing surface that federates ALL tenants, but
--   an oauth_clients row still requires a tenant_id NOT NULL. It is scoped to
--   tenant_id='gap' / tenant_type='B2B_ENTERPRISE' — the platform's own
--   operational tenant slug ('gap' is a reserved tenantId per
--   specs/features/multi-tenancy.md, so it can never collide with a
--   consumer-registered tenant). Operator identity + cross-tenant selection is
--   resolved by admin-service (operator JWT, ADR-002 tenant-scope sentinel),
--   NOT by this client's tenant_id.
--
-- Scopes:
--   openid + profile + email (system scopes from V0008) + tenant.read
--   (system scope from V0011) so the issued token carries tenant_id /
--   tenant_type claims the console needs for the registry + per-domain calls.
--
-- Redirect URI:
--   http://console.local/api/auth/callback — the console server route
--   (Traefik hostname console.local, ADR-MONO-013 / console-integration-contract
--   § 2.3). Exact match; no wildcard. localhost dev variant included for local
--   `next dev` parity with the fan-platform V0011 pattern.
--
-- Token TTLs (demo-spa-client / V0008 public-client pattern):
--   - access_token:        PT30M  = 1800 seconds
--   - refresh_token:       PT720H = 2592000 seconds
--   - authorization_code:  PT5M   = 300 seconds
--
-- Idempotency / isolation:
--   Distinct client_id 'platform-console-web' — no collision with existing
--   scm / fan / wms / ecommerce / demo-spa / test-internal clients. This
--   migration only INSERTs the new row; it does NOT touch any existing
--   oauth_clients or oauth_scopes row (system scopes already exist from
--   V0008/V0011 and are intentionally NOT re-inserted here).

-- ============================================================
-- platform-console-web
--   Operator console B2B delegation flow (authorization_code + refresh_token
--   + PKCE). Used by platform-console-web (Next.js) when an operator signs in.
--   PUBLIC client — no client_secret. PKCE enforced. refresh_token rotation
--   consistent with the demo-spa-client public-client lineage (ADR-003).
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
    'platform-console-web-id',
    'platform-console-web',
    'gap',
    'B2B_ENTERPRISE',
    NULL,
    'Platform Console Web',
    '["none"]',
    '["authorization_code","refresh_token"]',
    '["http://console.local/api/auth/callback","http://localhost:3000/api/auth/callback"]',
    '["openid","profile","email","tenant.read"]',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":true,"settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":false,"settings.token.x509-certificate-bound-access-tokens":false,"settings.token.access-token-time-to-live":["java.time.Duration",1800.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",2592000.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000],"settings.token.device-code-time-to-live":["java.time.Duration",300.000000000]}',
    NOW(),
    NOW()
);
