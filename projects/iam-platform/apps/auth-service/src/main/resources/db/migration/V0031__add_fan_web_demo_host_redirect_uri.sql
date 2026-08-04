-- ============================================================
-- TASK-BE-573 — register the fan web demo host callback
-- ============================================================
-- TASK-FAN-FE-014 containerizes `fan-platform-web` and serves it in the
-- integrated demo at `web.fan-platform.<DEMO_DOMAIN>` — sibling parity with
-- ecommerce's web-store at `web.ecommerce.<DEMO_DOMAIN>`. OAuth2 validates
--
-- (Angle brackets ON PURPOSE — do NOT write the dollar-brace form used by the
--  shell and by compose anywhere in this file, not even inside a comment.
--  Flyway runs placeholder substitution over the WHOLE file, comments included,
--  and an unresolvable one fails the migration with "No value provided for
--  placeholder", which aborts auth-service startup entirely. Measured twice
--  here: the first bring-up died on this header, and the fix died again on the
--  comment that explained the fix.)
-- `redirect_uri` by EXACT match (RFC 6749 §3.1.2.3 / Spring Authorization
-- Server), so that hostname has to be registered here.
--
-- Why the runtime demo seed does not cover it
-- ------------------------------------------------------------
-- `infra/demo/seed-demo-domain.sh` rewrites the registered URIs for the
-- runtime-chosen demo domain:
--
--     REPLACE(uri, '.local/', '.<DEMO_DOMAIN>/')  WHERE uri LIKE '%.local/%'
--
-- It REWRITES what is already registered; it does not INVENT hostnames. So it
-- carries `fan-platform.local` to `fan-platform.<ip>.sslip.io`, and — once this
-- migration lands — it will carry `web.fan-platform.local` too. Without this
-- row there is nothing for it to rewrite.
--
-- The failure mode if this is missing (measured, see seed-demo-domain.sh header)
-- ------------------------------------------------------------
--     HTTP/1.1 401
--     {"code":"UNAUTHORIZED","message":"Missing or invalid internal credentials"}
--
-- The message names neither the redirect_uri nor the client. Every container is
-- healthy and the login form returns 200 — only the callback dies.
--
-- Sibling precedent
-- ------------------------------------------------------------
-- V0012 registered `http://web.ecommerce.local/api/auth/callback/gap` in the
-- web-store client's SEED row. A new web hostname comes with a registration
-- migration in this repo; this is that migration for fan-platform.
--
-- Why string REPLACE and not JSON_ARRAY_APPEND
-- ------------------------------------------------------------
-- The SAS slice tests run on H2, production and Testcontainers on MySQL.
-- `JSON_SET`/`JSON_ARRAY`/`JSON_ARRAY_APPEND` are MySQL-only and break the H2
-- slice tests — V0011's own header records that constraint, and V0028 solved
-- the identical problem with `REPLACE()` on the serialized JSON text. Same
-- shape here.
--
-- Path is `/api/auth/callback/iam`, NOT `/gap`: V0024 rewrote every consumer
-- callback when the platform slug changed, and the fan web's next-auth
-- provider id is `'iam'` (`src/shared/auth/auth.ts`).
--
-- Idempotency: the WHERE guard skips rows that already carry the new host, so
-- re-applying the same logic never grows the array.
-- ============================================================

-- 1. redirect_uris — anchor on the `fan-platform.local` entry that V0011 seeded
--    and V0024 rewrote to `/iam`, and splice the new host in after it.
UPDATE oauth_clients
   SET redirect_uris = REPLACE(
           redirect_uris,
           'http://fan-platform.local/api/auth/callback/iam',
           'http://fan-platform.local/api/auth/callback/iam","http://web.fan-platform.local/api/auth/callback/iam'),
       updated_at = NOW()
 WHERE client_id = 'fan-platform-user-flow-client'
   AND redirect_uris LIKE '%http://fan-platform.local/api/auth/callback/iam%'
   AND redirect_uris NOT LIKE '%web.fan-platform.local%';

-- 2. post_logout_redirect_uris — lives inside `client_settings` as a Jackson
--    default-typed list:
--      "settings.client.post-logout-redirect-uris": ["java.util.ArrayList", [ ... ]]
--    Element [0] is the TYPE TAG string, the real array is [1] (V0016/V0021).
--    Operating on the serialized text sidesteps that trap entirely.
UPDATE oauth_clients
   SET client_settings = REPLACE(
           client_settings,
           '"http://fan-platform.local/"',
           '"http://fan-platform.local/","http://web.fan-platform.local/"'),
       updated_at = NOW()
 WHERE client_id = 'fan-platform-user-flow-client'
   AND client_settings LIKE '%"http://fan-platform.local/"%'
   AND client_settings NOT LIKE '%web.fan-platform.local%';
