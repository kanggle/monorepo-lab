-- TASK-PC-FE-033
-- Register `post-logout-redirect-uris` on the platform-console-web PUBLIC
-- client so the console can drive OIDC RP-initiated logout
-- (`/connect/logout` end_session). Without a registered post-logout URI the
-- SAS OidcLogoutEndpoint rejects the `post_logout_redirect_uri` the console
-- sends, so the browser cannot be returned to the console login page after the
-- IdP session is terminated.
--
-- ------------------------------------------------------------------------
-- Background
-- ------------------------------------------------------------------------
-- V0015 seeded platform-console-web with client_settings carrying only
-- `require-proof-key` + `require-authorization-consent` — NO
-- `post-logout-redirect-uris`. V0016 fixed the default-typing of the
-- fan-platform / ecommerce clients' post-logout arrays but deliberately left
-- platform-console-web untouched (it had no such key). Before this migration
-- the console logout was local-only (token revoke + cookie clear) and the SAS
-- login session survived, silently re-authenticating the next login.
--
-- ------------------------------------------------------------------------
-- Form & correctness (mirrors V0016 cycle-3)
-- ------------------------------------------------------------------------
-- OAuthClientMapper.buildSasMapper() enables
-- SecurityJackson2Modules.enableDefaultTyping(mapper). A COLLECTION value
-- inside the UnmodifiableMap client_settings must be written as a
-- [typeId, value] wrapper-array whose typeId is SAS-allow-listed
-- (java.util.ArrayList is). So the registered value is:
--   "settings.client.post-logout-redirect-uris":
--       ["java.util.ArrayList",["http://console.local/login",
--                               "http://localhost:3000/login"]]
-- Both post-logout targets mirror the V0015 redirect_uris pair (dev
-- console.local + local/e2e localhost:3000). The console sends exactly
-- the NEXT_PUBLIC_APP_URL origin + "/login" (no query string) as the
-- post_logout_redirect_uri; SAS matches it exactly against this registered set.
--
-- ------------------------------------------------------------------------
-- Idempotency & portability
-- ------------------------------------------------------------------------
-- Structural JSON_SET on the parsed tree (immune to MySQL key-reorder /
-- whitespace / number normalization — the V0016 cycle-3 lesson). NULL-safe
-- idempotency guard: only set when the key is ABSENT
-- (`JSON_CONTAINS_PATH(...) = 0` is always TRUE/FALSE, never NULL, so it can
-- never silently exclude the target row the way `NULL <> 'x'` did in V0016
-- cycle 2). Re-running on an already-seeded DB is a no-op. MySQL-only JSON
-- functions are deliberate (Flyway runs only against MySQL 8.0 in this
-- service; the sole H2 slice test disables Flyway). Forward-only — no down
-- migration (data-model.md migration policy).

UPDATE oauth_clients
SET client_settings = JSON_SET(
        client_settings,
        '$."settings.client.post-logout-redirect-uris"',
        JSON_ARRAY('java.util.ArrayList',
                   JSON_ARRAY('http://console.local/login',
                              'http://localhost:3000/login'))
    )
WHERE client_id = 'platform-console-web'
  AND JSON_CONTAINS_PATH(
        client_settings, 'one',
        '$."settings.client.post-logout-redirect-uris"'
      ) = 0;
