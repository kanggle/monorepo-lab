-- TASK-BE-297
-- Corrective forward migration: re-serialize the array-valued custom
-- `settings.client.post-logout-redirect-uris` ClientSettings entry seeded by
-- V0011 (fan-platform) and V0012 (ecommerce) into the SAS / SecurityJackson2-
-- Modules default-typed form.
--
-- ------------------------------------------------------------------------
-- Defect (pre-existing, predates BE-296; discovered during BE-296 review)
-- ------------------------------------------------------------------------
-- OAuthClientMapper.buildSasMapper() enables
-- SecurityJackson2Modules.enableDefaultTyping(mapper). Under polymorphic
-- default typing, a COLLECTION value inside the (UnmodifiableMap) client_settings
-- map must be written as a [typeId, value] wrapper-array whose typeId is an
-- allow-listed concrete type (the SAS AllowlistTypeIdResolver allows
-- java.util.ArrayList, among others). The Duration / OAuth2TokenFormat values in
-- every seed already use this envelope:
--     "settings.token.access-token-time-to-live":["java.time.Duration",900.000000000]
--
-- V0011 / V0012 instead hand-wrote post-logout-redirect-uris as a PLAIN JSON
-- array with NO envelope:
--     "settings.client.post-logout-redirect-uris":
--         ["http://localhost:3000/","http://fan-platform.local/"]
-- On read, default typing treats element 0 ("http://localhost:3000/") as a Java
-- type id, which is not resolvable / not allow-listed →
-- com.fasterxml.jackson.databind.exc.InvalidTypeIdException →
-- OAuthClientMapper.deserializeClientSettings throws OAuthClientMappingException.
--
-- Severity: PRODUCTION break (not test-only). JpaRegisteredClientRepository is
-- the production RegisteredClientRepository @Component bean wired into the
-- @Order(1) SAS filter chain. EVERY real authorization_code / refresh_token /
-- /oauth2/authorize / token-endpoint request from fan-platform-user-flow-client,
-- ecommerce-web-store-client, or ecommerce-admin-dashboard-client resolves the
-- client through findByClientId → OAuthClientMapper.toRegisteredClient and
-- therefore throws. It stayed latent only because every prior integration test
-- exercised the full RegisteredClient mapping path solely for the clean V0008
-- clients (demo-spa-client / test-internal-client), never these three rows.
--
-- ------------------------------------------------------------------------
-- Fix strategy
-- ------------------------------------------------------------------------
-- Option (a) from the task: corrective forward Flyway migration that
-- re-serializes ONLY the three affected rows' client_settings into the typed
-- form. The shared OAuthClientMapper / SecurityJackson2Modules config and ALL
-- already-clean clients (demo-spa-client / test-internal-client / wms / scm /
-- platform-console-web) are left untouched — least blast radius.
--
-- The corrective value wraps the existing 2-element string array in the
-- allow-listed java.util.ArrayList envelope:
--     "settings.client.post-logout-redirect-uris":
--         ["java.util.ArrayList",["http://localhost:3000/","http://fan-platform.local/"]]
-- This deserializes to the byte-equivalent effective ClientSettings: the same
-- key holding the same ordered List<String> of the same URIs. Verified
-- field-by-field by OAuthClientMapperTest (typed form round-trips; original
-- plain form throws).
--
-- ------------------------------------------------------------------------
-- Portability & idempotency
-- ------------------------------------------------------------------------
-- Uses ANSI REPLACE() string substitution (NOT MySQL JSON_SET / JSON_ARRAY) so
-- the migration runs identically on MySQL (production + Testcontainers
-- integrationTest) and H2 (SAS slice tests) — the same reason V0011/V0012
-- embedded the field inline (PR #144).
--
-- Idempotent / conditional: the WHERE guard matches ONLY rows still carrying
-- the exact broken plain-array substring. Once corrected (or on a fresh DB
-- where V0011/V0012 were authored correctly in a future edit) the predicate
-- does not match and REPLACE() is additionally a no-op on the absent substring.
-- Forward-only — no down migration (consistent with the migration policy in
-- specs/services/auth-service/data-model.md).

-- fan-platform-user-flow-client (V0011)
UPDATE oauth_clients
SET client_settings = REPLACE(
        client_settings,
        '"settings.client.post-logout-redirect-uris":["http://localhost:3000/","http://fan-platform.local/"]',
        '"settings.client.post-logout-redirect-uris":["java.util.ArrayList",["http://localhost:3000/","http://fan-platform.local/"]]'
    )
WHERE client_id = 'fan-platform-user-flow-client'
  AND client_settings LIKE '%"settings.client.post-logout-redirect-uris":["http://localhost:3000/","http://fan-platform.local/"]%';

-- ecommerce-web-store-client (V0012)
UPDATE oauth_clients
SET client_settings = REPLACE(
        client_settings,
        '"settings.client.post-logout-redirect-uris":["http://localhost:3000/","http://web.ecommerce.local/"]',
        '"settings.client.post-logout-redirect-uris":["java.util.ArrayList",["http://localhost:3000/","http://web.ecommerce.local/"]]'
    )
WHERE client_id = 'ecommerce-web-store-client'
  AND client_settings LIKE '%"settings.client.post-logout-redirect-uris":["http://localhost:3000/","http://web.ecommerce.local/"]%';

-- ecommerce-admin-dashboard-client (V0012)
UPDATE oauth_clients
SET client_settings = REPLACE(
        client_settings,
        '"settings.client.post-logout-redirect-uris":["http://localhost:3001/","http://admin.ecommerce.local/"]',
        '"settings.client.post-logout-redirect-uris":["java.util.ArrayList",["http://localhost:3001/","http://admin.ecommerce.local/"]]'
    )
WHERE client_id = 'ecommerce-admin-dashboard-client'
  AND client_settings LIKE '%"settings.client.post-logout-redirect-uris":["http://localhost:3001/","http://admin.ecommerce.local/"]%';
