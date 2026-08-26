-- ============================================================
-- TASK-BE-582 — register the fan Vercel production domain callback
-- ============================================================
-- ADR-MONO-067 phase 4 serves fan-platform-web from Vercel at the canonical
-- public hostname `fan.hubwang.com` (TEMPLATE.md, the PUBLIC-HOSTNAMES block
-- that TASK-MONO-584 made canonical and check-public-domains.sh guards).
-- OAuth2 validates `redirect_uri` by EXACT match (RFC 6749 3.1.2.3 / Spring
-- Authorization Server), so that hostname has to be registered here.
--
-- (Angle brackets ON PURPOSE below — do NOT write the dollar-brace form used
--  by the shell and by compose anywhere in this file, not even inside a
--  comment. Flyway runs placeholder substitution over the WHOLE file, comments
--  included, and an unresolvable one fails the migration with "No value
--  provided for placeholder", which aborts auth-service startup entirely.
--  V0031 died on this TWICE: first on its header, then again on the very
--  comment that explained the fix.)
--
-- This is the first https:// entry in the table
-- ------------------------------------------------------------
-- Every one of the 14 URIs registered before this migration is plain http://
-- (measured 2026-08-26). Vercel is HTTPS-only and terminates TLS itself, so
-- the scheme is not optional here — an http:// spelling of the same host would
-- never be sent by the browser and would fail the exact-match check.
--
-- Why the runtime demo seed does not cover it — and why that is the POINT
-- ------------------------------------------------------------
-- `infra/demo/seed-demo-domain.sh` rewrites the registered URIs for the
-- runtime-chosen demo domain:
--
--     REPLACE(jt.uri, '.local/', @dom)   WHERE jt.uri LIKE '%.local/%'
--
-- V0031's header named the rule: it REWRITES what is already registered; it
-- does not INVENT hostnames. `fan.hubwang.com` does not match `%.local/%`, so
-- the demo seed never touches this row — the entry stays byte-identical across
-- every boot.
--
-- That is exactly what makes a custom domain cheaper than the Vercel-assigned
-- one: `*.vercel.app` preview URLs change per deployment, so no fixed
-- `redirect_uri` could ever be pinned for them. A domain we own is stable, so
-- this migration runs once and is done. (TASK-MONO-574 recorded that reasoning
-- in prose; the predicate above is what makes it true.)
--
-- Scope: fan ONLY — the other surfaces are not guesses to be made here
-- ------------------------------------------------------------
-- The canonical table also names `console.hubwang.com` and
-- `store.hubwang.com`, and both are deliberately absent from this migration.
-- Registered callback PATHS differ per client — four shapes coexist today:
--
--     /api/auth/callback        platform-console      (V0015)
--     /api/auth/callback/gap    ecommerce, fan legacy
--     /api/auth/callback/iam    fan current           (V0024 rewrote it)
--     /callback                 localhost:9001 etc.
--
-- Registering a hostname with the wrong path shape fails as
-- `redirect_uri_mismatch`, and that error names neither the URI nor the
-- client. Each surface gets its own migration when its phase lands and its
-- path has been measured rather than assumed. V0031 is that precedent.
--
-- The failure mode if this row is missing (measured, V0031 header /
-- seed-demo-domain.sh header)
-- ------------------------------------------------------------
--     HTTP/1.1 401
--     {"code":"UNAUTHORIZED","message":"Missing or invalid internal credentials"}
--
-- Every container is healthy and the login form returns 200 — only the
-- callback dies, and the message names neither the redirect_uri nor the
-- client. TASK-MONO-574 measures the HTTPS-front/plaintext-IdP round trip;
-- without this row that measurement would read a configuration gap as a
-- scheme-boundary verdict, and D4 would be decided on a false premise.
--
-- Why string REPLACE and not JSON_ARRAY_APPEND
-- ------------------------------------------------------------
-- The SAS slice tests run on H2, production and Testcontainers on MySQL.
-- `JSON_SET`/`JSON_ARRAY`/`JSON_ARRAY_APPEND` are MySQL-only and break the H2
-- slice tests — V0011's header records that constraint, V0028 and V0031 both
-- solved the identical problem with `REPLACE()` on the serialized JSON text.
-- Same shape here.
--
-- Anchor: `web.fan-platform.local`, the LAST element V0031 left in both
-- arrays, so the new entry appends at the tail and the assertion order in
-- OAuthClientPostLogoutRedirectUriSeedIntegrationTest stays readable as a
-- migration-ordered history.
--
-- Idempotency: the WHERE guard skips rows that already carry the new host, so
-- re-applying the same logic never grows the array.
-- ============================================================

-- 1. redirect_uris — append after the web.fan-platform.local entry V0031 added.
UPDATE oauth_clients
   SET redirect_uris = REPLACE(
           redirect_uris,
           'http://web.fan-platform.local/api/auth/callback/iam',
           'http://web.fan-platform.local/api/auth/callback/iam","https://fan.hubwang.com/api/auth/callback/iam'),
       updated_at = NOW()
 WHERE client_id = 'fan-platform-user-flow-client'
   AND redirect_uris LIKE '%http://web.fan-platform.local/api/auth/callback/iam%'
   AND redirect_uris NOT LIKE '%fan.hubwang.com%';

-- 2. post_logout_redirect_uris — lives inside `client_settings` as a Jackson
--    default-typed list:
--      "settings.client.post-logout-redirect-uris": ["java.util.ArrayList", [ ... ]]
--    Element [0] is the TYPE TAG string, the real array is [1] (V0016/V0021).
--    Operating on the serialized text sidesteps that trap entirely.
UPDATE oauth_clients
   SET client_settings = REPLACE(
           client_settings,
           '"http://web.fan-platform.local/"',
           '"http://web.fan-platform.local/","https://fan.hubwang.com/"'),
       updated_at = NOW()
 WHERE client_id = 'fan-platform-user-flow-client'
   AND client_settings LIKE '%"http://web.fan-platform.local/"%'
   AND client_settings NOT LIKE '%fan.hubwang.com%';
