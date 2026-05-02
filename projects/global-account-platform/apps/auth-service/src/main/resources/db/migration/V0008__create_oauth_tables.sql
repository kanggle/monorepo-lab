-- TASK-BE-252
-- OAuth 2.0 / OIDC persistence tables for Spring Authorization Server (SAS).
-- Replaces in-memory placeholder clients (TASK-BE-251) with JPA-backed storage.
--
-- Tables created:
--   oauth_clients          — registered OAuth 2.0 clients (multi-tenant)
--   oauth_scopes           — scope catalog (system-wide + tenant-scoped)
--   oauth_consent          — user grant records (principal × client × scopes)
--   oauth2_authorization   — SAS canonical authorization state (tokens, codes)
--
-- Spring Authorization Server canonical schema source:
--   spring-authorization-server/oauth2-authorization-schema.sql (1.x branch)

-- ============================================================
-- oauth_clients
-- Stores registered OAuth 2.0 clients.  Lookup is always by
-- globally-unique client_id.  tenant_id is stored here so that
-- TenantClaimTokenCustomizer can read it from ClientSettings
-- without parsing the clientName hack used in TASK-BE-251.
-- ============================================================
CREATE TABLE oauth_clients (
    id                              VARCHAR(100)    NOT NULL,
    client_id                       VARCHAR(100)    NOT NULL,
    tenant_id                       VARCHAR(32)     NOT NULL,
    tenant_type                     VARCHAR(32)     NOT NULL,
    client_secret_hash              VARCHAR(200)    NULL        COMMENT 'BCrypt hash; NULL for public PKCE clients',
    client_name                     VARCHAR(200)    NOT NULL,
    client_authentication_methods   JSON            NOT NULL    COMMENT 'e.g. ["client_secret_basic"]',
    authorization_grant_types       JSON            NOT NULL,
    redirect_uris                   JSON            NOT NULL    DEFAULT (JSON_ARRAY()),
    scopes                          JSON            NOT NULL,
    client_settings                 JSON            NOT NULL    COMMENT 'SAS ClientSettings serialized',
    token_settings                  JSON            NOT NULL    COMMENT 'SAS TokenSettings serialized',
    created_at                      TIMESTAMP       NOT NULL    DEFAULT CURRENT_TIMESTAMP,
    updated_at                      TIMESTAMP       NOT NULL    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_oauth_clients_client_id (client_id),
    INDEX idx_oauth_clients_tenant_client (tenant_id, client_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- oauth_scopes
-- Scope catalog.  System scopes have tenant_id = NULL and are
-- shared across all tenants.  Tenant-specific scopes carry the
-- owning tenant_id.
-- Unique constraint: (scope_name, COALESCE(tenant_id, '__system__'))
-- Implemented via generated column + unique index (MySQL 8 ≥ 5.7.6).
-- ============================================================
CREATE TABLE oauth_scopes (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    scope_name      VARCHAR(100)    NOT NULL,
    tenant_id       VARCHAR(32)     NULL        COMMENT 'NULL = system scope shared across tenants',
    description     VARCHAR(500)    NULL,
    is_system       BOOLEAN         NOT NULL    DEFAULT FALSE,
    created_at      TIMESTAMP       NOT NULL    DEFAULT CURRENT_TIMESTAMP,
    -- Generated column to support unique index across nullable tenant_id
    tenant_scope_key VARCHAR(132) AS (CONCAT(COALESCE(tenant_id, '__system__'), ':', scope_name)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_oauth_scopes_tenant_scope (tenant_scope_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- oauth_consent
-- Records what scopes a principal has consented to for a client.
-- revoked_at NULL means consent is active.
-- ============================================================
CREATE TABLE oauth_consent (
    principal_id    VARCHAR(100)    NOT NULL,
    client_id       VARCHAR(100)    NOT NULL,
    tenant_id       VARCHAR(32)     NOT NULL,
    granted_scopes  JSON            NOT NULL,
    granted_at      TIMESTAMP       NOT NULL    DEFAULT CURRENT_TIMESTAMP,
    revoked_at      TIMESTAMP       NULL,
    PRIMARY KEY (principal_id, client_id),
    INDEX idx_oauth_consent_tenant_principal (tenant_id, principal_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- oauth2_authorization
-- Spring Authorization Server canonical schema (SAS 1.x).
-- Do NOT rename columns — JdbcOAuth2AuthorizationService depends on them.
-- Source: https://github.com/spring-projects/spring-authorization-server
--         /blob/main/oauth2-authorization-server/src/main/resources/
--         org/springframework/security/oauth2/server/authorization/
--         oauth2-authorization-schema.sql
-- ============================================================
CREATE TABLE oauth2_authorization (
    id                            VARCHAR(100)  NOT NULL,
    registered_client_id          VARCHAR(100)  NOT NULL,
    principal_name                VARCHAR(200)  NOT NULL,
    authorization_grant_type      VARCHAR(100)  NOT NULL,
    authorized_scopes             VARCHAR(1000) DEFAULT NULL,
    attributes                    BLOB          DEFAULT NULL,
    state                         VARCHAR(500)  DEFAULT NULL,
    authorization_code_value      BLOB          DEFAULT NULL,
    authorization_code_issued_at  TIMESTAMP     DEFAULT NULL,
    authorization_code_expires_at TIMESTAMP     DEFAULT NULL,
    authorization_code_metadata   BLOB          DEFAULT NULL,
    access_token_value            BLOB          DEFAULT NULL,
    access_token_issued_at        TIMESTAMP     DEFAULT NULL,
    access_token_expires_at       TIMESTAMP     DEFAULT NULL,
    access_token_metadata         BLOB          DEFAULT NULL,
    access_token_type             VARCHAR(100)  DEFAULT NULL,
    access_token_scopes           VARCHAR(1000) DEFAULT NULL,
    oidc_id_token_value           BLOB          DEFAULT NULL,
    oidc_id_token_issued_at       TIMESTAMP     DEFAULT NULL,
    oidc_id_token_expires_at      TIMESTAMP     DEFAULT NULL,
    oidc_id_token_metadata        BLOB          DEFAULT NULL,
    refresh_token_value           BLOB          DEFAULT NULL,
    refresh_token_issued_at       TIMESTAMP     DEFAULT NULL,
    refresh_token_expires_at      TIMESTAMP     DEFAULT NULL,
    refresh_token_metadata        BLOB          DEFAULT NULL,
    user_code_value               BLOB          DEFAULT NULL,
    user_code_issued_at           TIMESTAMP     DEFAULT NULL,
    user_code_expires_at          TIMESTAMP     DEFAULT NULL,
    user_code_metadata            BLOB          DEFAULT NULL,
    device_code_value             BLOB          DEFAULT NULL,
    device_code_issued_at         TIMESTAMP     DEFAULT NULL,
    device_code_expires_at        TIMESTAMP     DEFAULT NULL,
    device_code_metadata          BLOB          DEFAULT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Seed data: system scopes (is_system=true, tenant_id=NULL)
-- All tenants may request these scopes.
-- ============================================================
INSERT INTO oauth_scopes (scope_name, tenant_id, description, is_system, created_at) VALUES
    ('openid',         NULL, 'OpenID Connect — required for ID token issuance',  TRUE, NOW()),
    ('profile',        NULL, 'OIDC profile claim set',                            TRUE, NOW()),
    ('email',          NULL, 'OIDC email claim set',                              TRUE, NOW()),
    ('offline_access', NULL, 'Allow refresh_token issuance',                      TRUE, NOW());

-- ============================================================
-- Seed data: placeholder clients migrated from TASK-BE-251
-- in-memory beans.  BCrypt hash of "secret" (cost 10).
-- Verified: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
--   matches "secret" via BCryptPasswordEncoder.
--
-- test-internal-client — client_credentials (service-to-service)
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
    'test-internal-client-id',
    'test-internal-client',
    'fan-platform',
    'B2C',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'Test Internal Client',
    '["client_secret_basic"]',
    '["client_credentials"]',
    '[]',
    '["account.read","openid"]',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":true,"settings.token.x509-certificate-bound-access-tokens":false,"settings.token.access-token-time-to-live":["java.time.Duration",1800.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",2592000.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000],"settings.token.device-code-time-to-live":["java.time.Duration",300.000000000]}',
    NOW(),
    NOW()
);

-- ============================================================
-- demo-spa-client — authorization_code + PKCE (B2C SPA)
-- Public client: no client_secret_hash (NULL)
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
    'demo-spa-client-id',
    'demo-spa-client',
    'fan-platform',
    'B2C',
    NULL,
    'Demo SPA Client',
    '["none"]',
    '["authorization_code","refresh_token"]',
    '["http://localhost:3000/callback"]',
    '["openid","profile","email"]',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":true,"settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":false,"settings.token.x509-certificate-bound-access-tokens":false,"settings.token.access-token-time-to-live":["java.time.Duration",1800.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",2592000.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000],"settings.token.device-code-time-to-live":["java.time.Duration",300.000000000]}',
    NOW(),
    NOW()
);
