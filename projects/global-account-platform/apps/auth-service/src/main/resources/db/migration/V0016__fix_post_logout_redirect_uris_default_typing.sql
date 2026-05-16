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
-- Why the first V0016 attempt (ANSI REPLACE on a pre-normalization literal)
-- was a NO-OP — root cause of PR #571's 3 IT failures
-- ------------------------------------------------------------------------
-- `oauth_clients.client_settings` is a MySQL native **JSON** column
-- (V0008__create_oauth_tables.sql:32 — `client_settings JSON NOT NULL`).
-- MySQL normalizes every JSON value on STORE:
--   1. object members are re-ordered (not insertion order),
--   2. insignificant whitespace is stripped AND MySQL's canonical
--      JSON→string rendering reinserts a space after every ':' and ',',
--   3. numbers are renormalized (e.g. 900.000000000 → 900.0).
-- Consequently the V0011/V0012 row's stored text is NOT the byte sequence the
-- seed file wrote. Worse: `REPLACE(json_col, lit, rep)` implicitly casts the
-- JSON value to its *normalized* string form before substituting. The first
-- V0016 attempt searched for the **pre-normalization seed literal**
--   '"settings.client.post-logout-redirect-uris":["http://localhost:3000/",...]'
-- (no spaces, original key order). That substring never appears in the
-- normalized stored text (spaces after ':'/',' , reordered keys), so both the
-- REPLACE() and its LIKE guard matched nothing — a silent no-op. The IT still
-- read the untouched plain array and still threw InvalidTypeIdException citing
-- 'http://localhost:3000/' as the type id, exactly as before the migration.
--
-- The previous OAuthClientMapperTest passed because it fed the mapper a
-- hand-built Java String (no MySQL JSON round-trip), so it never exercised the
-- normalization that defeats a text-substring REPLACE. A new slice/IT-layer
-- assertion (see test changes for TASK-BE-297) now pins the ACTUAL
-- post-migration stored value as read back through the JSON column, so a
-- no-op migration fails fast at the non-Docker layer instead of only after a
-- CI Testcontainers cycle.
--
-- ------------------------------------------------------------------------
-- Fix strategy (corrected)
-- ------------------------------------------------------------------------
-- Mutate the value STRUCTURALLY with MySQL JSON functions, which operate on
-- the parsed JSON tree and are therefore immune to key ordering, whitespace
-- and number renormalization. JSON_SET replaces the plain array value with the
-- allow-listed [typeId, value] wrapper-array required by SAS default typing:
--     "settings.client.post-logout-redirect-uris":
--         ["java.util.ArrayList",["http://localhost:3000/","http://fan-platform.local/"]]
-- The inner array is taken verbatim from the existing stored value via
-- JSON_EXTRACT, so the URIs (and their order) are preserved byte-for-byte
-- regardless of how MySQL normalized them on the original INSERT. This
-- deserializes to the byte-equivalent effective ClientSettings: the same key
-- holding the same ordered List<String> of the same URIs. The
-- java.util.ArrayList typeId is on the SAS AllowlistTypeIdResolver list and is
-- the exact wrapper position the clean clients' working
-- `["java.time.Duration",900.0]` envelope demonstrates SAS round-trips for a
-- typed value inside the UnmodifiableMap. Verified field-by-field by
-- OAuthClientMapperTest (typed form round-trips; original plain form throws)
-- and end-to-end by OAuthClientPostLogoutRedirectUriSeedIntegrationTest.
--
-- Only the shared OAuthClientMapper / SecurityJackson2Modules config and ALL
-- already-clean clients (demo-spa-client / test-internal-client / wms / scm /
-- platform-console-web) are left untouched — least blast radius.
--
-- ------------------------------------------------------------------------
-- Portability & idempotency
-- ------------------------------------------------------------------------
-- MySQL-only JSON functions are deliberate and correct here: Flyway runs ONLY
-- against MySQL 8.0 in this service. Every test that enables Flyway uses a
-- MySQLContainer("mysql:8.0"); the sole H2-backed test
-- (OAuth2AuthorizationServerSliceTest) sets `spring.flyway.enabled=false` and
-- builds its schema with Hibernate ddl-auto, so no migration ever executes on
-- H2. (The V0011/V0012 "embed inline so it stays H2-portable" comments predate
-- that slice test disabling Flyway; the H2 constraint no longer applies to the
-- migration path.) Production also runs MySQL 8.0. JSON_SET / JSON_EXTRACT /
-- JSON_TYPE are all available on MySQL 8.0.
--
-- Idempotent / conditional: each UPDATE's WHERE guard fires ONLY when the key
-- is still present AND its first element is NOT already the corrective type id
-- ('java.util.ArrayList'). On a corrected DB (or a future DB where V0011/V0012
-- were authored correctly) the predicate matches nothing and the statement is
-- a no-op. Forward-only — no down migration (consistent with the migration
-- policy in specs/services/auth-service/data-model.md).

SET @plr := '$."settings.client.post-logout-redirect-uris"';

-- fan-platform-user-flow-client (V0011)
UPDATE oauth_clients
SET client_settings = JSON_SET(
        client_settings,
        @plr,
        JSON_ARRAY('java.util.ArrayList', JSON_EXTRACT(client_settings, @plr))
    )
WHERE client_id = 'fan-platform-user-flow-client'
  AND JSON_CONTAINS_PATH(client_settings, 'one', @plr)
  AND JSON_UNQUOTE(JSON_EXTRACT(client_settings, CONCAT(@plr, '[0]')))
        <> 'java.util.ArrayList';

-- ecommerce-web-store-client (V0012)
UPDATE oauth_clients
SET client_settings = JSON_SET(
        client_settings,
        @plr,
        JSON_ARRAY('java.util.ArrayList', JSON_EXTRACT(client_settings, @plr))
    )
WHERE client_id = 'ecommerce-web-store-client'
  AND JSON_CONTAINS_PATH(client_settings, 'one', @plr)
  AND JSON_UNQUOTE(JSON_EXTRACT(client_settings, CONCAT(@plr, '[0]')))
        <> 'java.util.ArrayList';

-- ecommerce-admin-dashboard-client (V0012)
UPDATE oauth_clients
SET client_settings = JSON_SET(
        client_settings,
        @plr,
        JSON_ARRAY('java.util.ArrayList', JSON_EXTRACT(client_settings, @plr))
    )
WHERE client_id = 'ecommerce-admin-dashboard-client'
  AND JSON_CONTAINS_PATH(client_settings, 'one', @plr)
  AND JSON_UNQUOTE(JSON_EXTRACT(client_settings, CONCAT(@plr, '[0]')))
        <> 'java.util.ArrayList';
