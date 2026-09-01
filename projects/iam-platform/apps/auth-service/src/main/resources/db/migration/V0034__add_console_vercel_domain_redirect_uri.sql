-- ============================================================
-- TASK-BE-589 — register the console Vercel production domain callback
-- ============================================================
-- ADR-MONO-067 phase 3 serves platform-console-web from Vercel at the
-- canonical public hostname `console.hubwang.com` (TEMPLATE.md, the
-- PUBLIC-HOSTNAMES block that TASK-MONO-584 made canonical and
-- check-public-domains.sh guards). OAuth2 validates `redirect_uri` by EXACT
-- match (RFC 6749 3.1.2.3 / Spring Authorization Server), so that hostname has
-- to be registered here.
--
-- (Angle brackets ON PURPOSE below — do NOT write the dollar-brace form used
--  by the shell and by compose anywhere in this file, not even inside a
--  comment. Flyway runs placeholder substitution over the WHOLE file, comments
--  included, and an unresolvable one fails the migration with "No value
--  provided for placeholder", which aborts auth-service startup entirely.
--  V0031 died on this TWICE: first on its header, then again on the very
--  comment that explained the fix.)
--
-- Why this is a separate migration and not part of V0033
-- ------------------------------------------------------------
-- V0033 (TASK-BE-582) registered the fan surface and deliberately left console
-- and store out, with its reason stated in its own header: registered callback
-- PATHS differ per client, and registering a hostname with the wrong path shape
-- fails as `redirect_uri_mismatch` — an error that names neither the URI nor
-- the client. It asked each surface to measure its own path when its phase
-- lands. This is console's turn, and the measurement did NOT match fan:
--
--     fan      callback  /api/auth/callback/iam     post-logout  /
--     console  callback  /api/auth/callback         post-logout  /login
--
-- Both differences are real. `TASK-MONO-460` already recorded that the console
-- callback carries no `/iam` suffix, and the console's logout route builds
-- `new URL('/login', publicOrigin(env))` — the console lands on its login page,
-- not on its root. Copying the fan shape here would have been wrong twice.
--
-- Measured at the app, not at the migration (TASK-BE-589 AC-0 #5):
--   redirect_uri              = env.OIDC_REDIRECT_URI, sent verbatim
--                               (app/api/auth/login/route.ts,
--                                app/api/auth/callback/route.ts)
--   post_logout_redirect_uri  = new URL('/login', publicOrigin(env)), no query
--                               (app/api/auth/logout/route.ts)
--
-- The first https:// entry ON THIS CLIENT
-- ------------------------------------------------------------
-- V0033 was the first https:// row in the whole table. All four URIs currently
-- registered for platform-console-web are still plain http:// (V0015 seeded the
-- two callbacks, V0021 the two post-logout landings; V0020/V0023/V0024 touch
-- this client but not its URI columns, and V0028 touches only web-store/fan).
-- Vercel is HTTPS-only and terminates TLS itself, so the scheme is not optional
-- here — an http:// spelling of the same host would never be sent by the
-- browser and would fail the exact-match check.
--
-- Nothing validates the scheme (searched, TASK-BE-589 AC-3): OAuthClientMapper
-- passes each stored URI straight into `builder.redirectUri(uri)` with no
-- inspection, and the only redirect-uri validation in the service —
-- OAuthLoginUseCase.validateRedirectUri — belongs to the SOCIAL login path and
-- is a membership test against the provider's allow-list, not a scheme test.
-- So an http:// typo here would not be rejected at startup; it would fail
-- silently at the browser round trip. That is why the value is written once,
-- from the canonical table.
--
-- Why the runtime demo seed does not cover it — and why that is the POINT
-- ------------------------------------------------------------
-- `infra/demo/seed-demo-domain.sh` rewrites the registered URIs for the
-- runtime-chosen demo domain:
--
--     REPLACE(jt.uri, '.local/', @dom)   WHERE jt.uri LIKE '%.local/%'
--
-- It REWRITES what is already registered; it does not INVENT hostnames.
-- `console.hubwang.com` does not match `%.local/%`, so the demo seed never
-- touches these rows — the entries stay byte-identical across every boot. That
-- script's own header names the constraint from the other side: "do not use a
-- broad predicate — it would take `.local`, `localhost` and `hubwang.com`
-- together."
--
-- Scope: console ONLY
-- ------------------------------------------------------------
-- The canonical table also names `store.hubwang.com`, deliberately absent here
-- for the same reason console was absent from V0033: web-store's phase has not
-- landed and its path shape is its own to measure.
--
-- The failure mode if these rows are missing (measured, V0031 header /
-- seed-demo-domain.sh header)
-- ------------------------------------------------------------
--     HTTP/1.1 401
--     {"code":"UNAUTHORIZED","message":"Missing or invalid internal credentials"}
--
-- Every container is healthy and the login form returns 200 — only the callback
-- dies, and the message names neither the redirect_uri nor the client. The
-- console is an operator surface whose entire screen is behind that login, so
-- the symptom is an empty shell rather than a broken feature.
--
-- Why string REPLACE and not JSON_SET / JSON_ARRAY_APPEND
-- ------------------------------------------------------------
-- The SAS slice tests run on H2, production and Testcontainers on MySQL. Those
-- JSON functions are MySQL-only and break the H2 slice tests — V0011's header
-- records that constraint, and V0028/V0031/V0033 all solved the identical
-- problem with `REPLACE()` on the serialized JSON text. (V0021 did use JSON_SET
-- on this very client, justified in its own header by the H2 slice disabling
-- Flyway. The three most recent siblings converged on REPLACE; follow them.)
--
-- Anchors: the LAST element of each array — `localhost:3000` in both — so the
-- new entries append at the tail and the assertion order in
-- OAuthClientPostLogoutRedirectUriSeedIntegrationTest stays readable as a
-- migration-ordered history.
--
-- Idempotency: the WHERE guard skips rows that already carry the new host, so
-- re-applying the same logic never grows the arrays.
-- ============================================================

-- 1. redirect_uris — append after the localhost:3000 entry V0015 seeded.
UPDATE oauth_clients
   SET redirect_uris = REPLACE(
           redirect_uris,
           'http://localhost:3000/api/auth/callback',
           'http://localhost:3000/api/auth/callback","https://console.hubwang.com/api/auth/callback'),
       updated_at = NOW()
 WHERE client_id = 'platform-console-web'
   AND redirect_uris LIKE '%http://localhost:3000/api/auth/callback%'
   AND redirect_uris NOT LIKE '%console.hubwang.com%';

-- 2. post_logout_redirect_uris — lives inside `client_settings` as a Jackson
--    default-typed list:
--      "settings.client.post-logout-redirect-uris": ["java.util.ArrayList", [ ... ]]
--    Element [0] is the TYPE TAG string, the real array is [1] (V0016/V0021).
--    Operating on the serialized text sidesteps that trap entirely.
--
--    The anchor is quoted on both sides so it can only match a complete array
--    element. Unquoted, `http://localhost:3000/login` is also a prefix of
--    nothing else in this row today — but the quotes make that independent of
--    what a later migration appends.
UPDATE oauth_clients
   SET client_settings = REPLACE(
           client_settings,
           '"http://localhost:3000/login"',
           '"http://localhost:3000/login","https://console.hubwang.com/login"'),
       updated_at = NOW()
 WHERE client_id = 'platform-console-web'
   AND client_settings LIKE '%"http://localhost:3000/login"%'
   AND client_settings NOT LIKE '%console.hubwang.com%';
