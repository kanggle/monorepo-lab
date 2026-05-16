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
-- allow-listed concrete type (the SAS allowlist permits java.util.ArrayList,
-- among others). The Duration / OAuth2TokenFormat values in every seed already
-- use this envelope:
--     "settings.token.access-token-time-to-live":["java.time.Duration",900.000000000]
--
-- V0011 / V0012 instead hand-wrote post-logout-redirect-uris as a PLAIN JSON
-- array with NO envelope:
--     "settings.client.post-logout-redirect-uris":
--         ["http://localhost:3000/","http://fan-platform.local/"]
-- On read, default typing treats element 0 ("http://localhost:3000/") as a Java
-- type id, which is not resolvable / not allow-listed ->
-- com.fasterxml.jackson.databind.exc.InvalidTypeIdException ->
-- OAuthClientMapper.deserializeClientSettings throws OAuthClientMappingException.
--
-- Severity: PRODUCTION break (not test-only). JpaRegisteredClientRepository is
-- the production RegisteredClientRepository @Component bean wired into the
-- @Order(1) SAS filter chain. EVERY real authorization_code / refresh_token /
-- /oauth2/authorize / token-endpoint request from fan-platform-user-flow-client,
-- ecommerce-web-store-client, or ecommerce-admin-dashboard-client resolves the
-- client through findByClientId -> OAuthClientMapper.toRegisteredClient and
-- therefore throws. It stayed latent only because every prior integration test
-- exercised the full RegisteredClient mapping path solely for the clean V0008
-- clients (demo-spa-client / test-internal-client), never these three rows.
-- The corrective typed form is pinned at the non-Docker unit layer by
-- OAuthClientMapperTest (typed array deserializes; original plain array throws)
-- and end-to-end by OAuthClientPostLogoutRedirectUriSeedIntegrationTest.
--
-- ------------------------------------------------------------------------
-- Why the FIRST V0016 attempt (cycle 1) was a NO-OP
-- ------------------------------------------------------------------------
-- `oauth_clients.client_settings` is a MySQL native **JSON** column
-- (V0008__create_oauth_tables.sql:32 — `client_settings JSON NOT NULL`).
-- MySQL normalizes every JSON value on STORE (object members re-ordered,
-- whitespace canonicalized with a space after every ':'/','. numbers
-- renormalized). The cycle-1 attempt used ANSI `REPLACE()` on the
-- pre-normalization seed literal, which never appears in the normalized
-- stored text — a silent no-op. Cycle 2 rewrote it structurally with
-- JSON_SET / JSON_EXTRACT.
--
-- ------------------------------------------------------------------------
-- Why the SECOND V0016 attempt (cycle 2) was ALSO a NO-OP — root cause
-- ------------------------------------------------------------------------
-- Cycle 2 hoisted the JSON path into a MySQL user variable
--     SET @plr := '$."settings.client.post-logout-redirect-uris"';
-- and referenced @plr across the subsequent UPDATE statements (including
-- `JSON_CONTAINS_PATH(client_settings,'one',@plr)` and
-- `JSON_UNQUOTE(JSON_EXTRACT(client_settings, CONCAT(@plr,'[0]'))) <> ...`).
-- Flyway parses a migration into individual statements and executes each one;
-- the `SET @plr` did not reliably resolve for the later statements, so @plr
-- evaluated to NULL. `JSON_CONTAINS_PATH(doc,'one',NULL)` is NULL and
-- `NULL <> 'java.util.ArrayList'` is NULL (SQL three-valued logic), so the
-- WHERE clause excluded all three rows: the migration "succeeded" (Flyway
-- recorded it, the context started, the unrelated V0015 test passed) but
-- changed nothing. The IT still read the untouched plain array and still
-- threw InvalidTypeIdException citing 'http://localhost:3000/' — byte-identical
-- to cycle 1, the tell-tale of a zero-row UPDATE.
--
-- ------------------------------------------------------------------------
-- Fix strategy (cycle 3 — deterministic, no cross-statement state)
-- ------------------------------------------------------------------------
-- 1. NO user variable. The JSON path literal
--      '$."settings.client.post-logout-redirect-uris"'
--    is inlined verbatim in every function call of every statement, so each
--    UPDATE is wholly self-contained and immune to Flyway statement splitting.
-- 2. Structural mutation only. JSON_SET rebuilds the value on the parsed JSON
--    tree (immune to MySQL key-reorder / whitespace / number normalization):
--      "settings.client.post-logout-redirect-uris":
--          ["java.util.ArrayList",["http://localhost:3000/","http://fan-platform.local/"]]
--    The inner array is lifted verbatim via JSON_EXTRACT, so the URIs and
--    their order are preserved byte-for-byte. java.util.ArrayList is on the
--    SAS allowlist and is the same wrapper position the clean clients'
--    working ["java.time.Duration",900.0] envelope round-trips through.
-- 3. NULL-safe idempotency guard. The "already corrected?" predicate is
--      JSON_SEARCH(<extracted array>, 'one', 'java.util.ArrayList') IS NULL
--    `X IS NULL` always yields TRUE or FALSE (never NULL), so it can never
--    silently exclude a target row the way `NULL <> 'x'` did in cycle 2:
--      - plain (broken) array  -> no 'java.util.ArrayList' scalar -> JSON_SEARCH
--        returns NULL -> IS NULL = TRUE  -> row corrected
--      - already-wrapped value -> 'java.util.ArrayList' found at $[0] ->
--        JSON_SEARCH returns "$[0]" -> IS NULL = FALSE -> row skipped
--      - key absent            -> JSON_CONTAINS_PATH = 0 -> row skipped
--    URLs can never equal the exact scalar 'java.util.ArrayList', so the
--    discriminator is unambiguous.
--
-- Only the three affected rows are touched; the shared OAuthClientMapper /
-- SecurityJackson2Modules config and every already-clean client
-- (demo-spa-client / test-internal-client / wms / scm / platform-console-web)
-- are left untouched — least blast radius.
--
-- ------------------------------------------------------------------------
-- Portability & idempotency
-- ------------------------------------------------------------------------
-- MySQL-only JSON functions are deliberate and correct: Flyway runs ONLY
-- against MySQL 8.0 in this service. Every test that enables Flyway uses a
-- MySQLContainer("mysql:8.0"); the sole H2-backed test
-- (OAuth2AuthorizationServerSliceTest) sets `spring.flyway.enabled=false` and
-- builds its schema with Hibernate ddl-auto, so no migration ever executes on
-- H2. (The V0011/V0012 "embed inline so it stays H2-portable" comments predate
-- that slice test disabling Flyway; the H2 constraint no longer applies to the
-- migration path.) Production also runs MySQL 8.0. JSON_SET / JSON_EXTRACT /
-- JSON_SEARCH / JSON_CONTAINS_PATH are all available on MySQL 8.0.
--
-- Forward-only — no down migration (consistent with the migration policy in
-- specs/services/auth-service/data-model.md). Re-running on an already-corrected
-- DB is a no-op (idempotency guard above).

-- fan-platform-user-flow-client (V0011)
UPDATE oauth_clients
SET client_settings = JSON_SET(
        client_settings,
        '$."settings.client.post-logout-redirect-uris"',
        JSON_ARRAY('java.util.ArrayList',
                   JSON_EXTRACT(client_settings, '$."settings.client.post-logout-redirect-uris"'))
    )
WHERE client_id = 'fan-platform-user-flow-client'
  AND JSON_CONTAINS_PATH(client_settings, 'one', '$."settings.client.post-logout-redirect-uris"')
  AND JSON_SEARCH(
        JSON_EXTRACT(client_settings, '$."settings.client.post-logout-redirect-uris"'),
        'one', 'java.util.ArrayList'
      ) IS NULL;

-- ecommerce-web-store-client (V0012)
UPDATE oauth_clients
SET client_settings = JSON_SET(
        client_settings,
        '$."settings.client.post-logout-redirect-uris"',
        JSON_ARRAY('java.util.ArrayList',
                   JSON_EXTRACT(client_settings, '$."settings.client.post-logout-redirect-uris"'))
    )
WHERE client_id = 'ecommerce-web-store-client'
  AND JSON_CONTAINS_PATH(client_settings, 'one', '$."settings.client.post-logout-redirect-uris"')
  AND JSON_SEARCH(
        JSON_EXTRACT(client_settings, '$."settings.client.post-logout-redirect-uris"'),
        'one', 'java.util.ArrayList'
      ) IS NULL;

-- ecommerce-admin-dashboard-client (V0012)
UPDATE oauth_clients
SET client_settings = JSON_SET(
        client_settings,
        '$."settings.client.post-logout-redirect-uris"',
        JSON_ARRAY('java.util.ArrayList',
                   JSON_EXTRACT(client_settings, '$."settings.client.post-logout-redirect-uris"'))
    )
WHERE client_id = 'ecommerce-admin-dashboard-client'
  AND JSON_CONTAINS_PATH(client_settings, 'one', '$."settings.client.post-logout-redirect-uris"')
  AND JSON_SEARCH(
        JSON_EXTRACT(client_settings, '$."settings.client.post-logout-redirect-uris"'),
        'one', 'java.util.ArrayList'
      ) IS NULL;
