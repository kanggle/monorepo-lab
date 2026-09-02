-- ============================================================
-- TASK-BE-590 -- register the store Vercel production domain callback
-- ============================================================
-- ADR-MONO-067 phase 2 serves web-store from Vercel at the canonical public
-- hostname `store.hubwang.com` (TEMPLATE.md, the PUBLIC-HOSTNAMES block that
-- TASK-MONO-584 made canonical and check-public-domains.sh guards). OAuth2
-- validates `redirect_uri` by EXACT match (RFC 6749 3.1.2.3 / Spring
-- Authorization Server), so that hostname has to be registered here.
--
-- (Angle brackets ON PURPOSE below - do NOT write the dollar-brace form used
--  by the shell and by compose anywhere in this file, not even inside a
--  comment. Flyway runs placeholder substitution over the WHOLE file, comments
--  included, and an unresolvable one fails the migration with "No value
--  provided for placeholder", which aborts auth-service startup entirely.
--  V0031 died on this TWICE: first on its header, then again on the very
--  comment that explained the fix.)
--
-- ASCII only, on purpose: all 34 migrations before this one are pure ASCII
-- (measured). Flyway's file encoding is a startup-killing axis, so this file
-- does not become the first exception for the sake of a comment.
--
-- Why this arrives after V0033 (fan) and V0034 (console), not with them
-- ------------------------------------------------------------
-- V0033's header named `store.hubwang.com` as "deliberately absent", with the
-- rule that each surface gets its own migration "when its phase lands and its
-- path has been measured rather than assumed". Registered callback PATHS
-- differ per client -- four shapes coexist -- and a hostname registered with
-- the wrong shape fails as `redirect_uri_mismatch`, an error that names
-- neither the URI nor the client. So the shape had to be measured first.
--
-- The path shape WAS measured -- twice, independently (2026-09-02)
-- ------------------------------------------------------------
--   1. Source: web-store/src/shared/auth/auth.ts:76 declares the NextAuth
--      provider as `id: 'iam'`, and NextAuth v5 derives the callback route
--      from the provider id, giving /api/auth/callback/iam
--   2. Live DB census: TASK-MONO-606 AC-4 enumerated the registered clients
--      and recorded `ecommerce-web-store-client` -> /api/auth/callback/iam
--
-- Two sources that cannot share a mistake: one reads the app, one reads the
-- rows. That is the evidence V0033's rule asked for.
--
-- What this migration does NOT fix
-- ------------------------------------------------------------
-- `https://store.hubwang.com/api/auth/*` currently returns 500 on every route
-- (measured 2026-09-02; the sibling kanggle-fan returns 200 at the same
-- moment, and the store's own `/` returns 200 -- so the app is alive and this
-- is not a network artefact). The cause is on the OTHER side: that Vercel
-- project carries no auth environment at all, while kanggle-fan carries four
-- variables. Registering this row is a PREREQUISITE for that wiring, not a
-- substitute for it. TASK-MONO-610 AC-4b holds the five env lines, and the
-- owner must not set the client id/secret before this row exists, or the
-- round trip dies at `redirect_uri_mismatch` instead -- which looks like
-- "I set everything and it still does not work".
--
-- Why the runtime demo seed does not touch this row
-- ------------------------------------------------------------
-- `infra/demo/seed-demo-domain.sh` rewrites registered URIs for the
-- runtime-chosen demo domain:
--
--     REPLACE(jt.uri, '.local/', @dom)   WHERE jt.uri LIKE '%.local/%'
--
-- `store.hubwang.com` does not match `%.local/%`, so the entry stays
-- byte-identical across every boot. V0031's header named the rule: the seed
-- REWRITES what is already registered; it does not INVENT hostnames.
--
-- The anchor also survives, and that had to be checked separately
-- ------------------------------------------------------------
-- The anchor below is a `.local` URI, which the demo seed DOES process -- so
-- the obvious worry is that a second boot leaves this migration matching
-- nothing (a REPLACE with no anchor updates 0 rows and reports SUCCESS).
-- It does not: that script APPENDS the rewritten URI and deliberately keeps
-- the original -- its own comment says the two UPDATEs "only append", because
-- deleting the `.local` form would break local development. So
-- `http://web.ecommerce.local/...` is present on a fresh volume and on a
-- demo-seeded one alike. The appended sibling is a different string
-- (`web.ecommerce.<demo domain>`), so exactly one occurrence matches.
--
-- Why string REPLACE and not JSON_ARRAY_APPEND
-- ------------------------------------------------------------
-- The SAS slice tests run on H2, production and Testcontainers on MySQL.
-- `JSON_SET`/`JSON_ARRAY`/`JSON_ARRAY_APPEND` are MySQL-only and break the H2
-- slice tests -- V0011's header records that constraint; V0028, V0031 and
-- V0033 all solved the identical problem with `REPLACE()` on the serialized
-- JSON text. Same shape here.
--
-- Anchor: `web.ecommerce.local`, the LAST element of both arrays after V0028
-- (which inserted `localhost:3001` directly AFTER `localhost:3000`, not at the
-- tail -- so "the last element" is not the one the migration order suggests,
-- and had to be read off the V0012 -> V0016 -> V0024 -> V0028 chain).
-- Anchoring there makes the new entry land at the tail, so the assertion order
-- in OAuthClientPostLogoutRedirectUriSeedIntegrationTest keeps reading as a
-- migration-ordered history.
--
-- Idempotency: the WHERE guard skips rows that already carry the new host, so
-- re-applying the same logic never grows the array.
-- ============================================================

-- 1. redirect_uris -- append after the web.ecommerce.local entry.
UPDATE oauth_clients
   SET redirect_uris = REPLACE(
           redirect_uris,
           'http://web.ecommerce.local/api/auth/callback/iam',
           'http://web.ecommerce.local/api/auth/callback/iam","https://store.hubwang.com/api/auth/callback/iam'),
       updated_at = NOW()
 WHERE client_id = 'ecommerce-web-store-client'
   AND redirect_uris LIKE '%http://web.ecommerce.local/api/auth/callback/iam%'
   AND redirect_uris NOT LIKE '%store.hubwang.com%';

-- 2. post_logout_redirect_uris -- lives inside `client_settings` as a Jackson
--    default-typed list:
--      "settings.client.post-logout-redirect-uris": ["java.util.ArrayList", [ ... ]]
--    Element [0] is the TYPE TAG string, the real array is [1] (V0016/V0021).
--    Operating on the serialized text sidesteps that trap entirely.
UPDATE oauth_clients
   SET client_settings = REPLACE(
           client_settings,
           '"http://web.ecommerce.local/"',
           '"http://web.ecommerce.local/","https://store.hubwang.com/"'),
       updated_at = NOW()
 WHERE client_id = 'ecommerce-web-store-client'
   AND client_settings LIKE '%"http://web.ecommerce.local/"%'
   AND client_settings NOT LIKE '%store.hubwang.com%';
