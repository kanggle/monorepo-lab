-- ============================================================
-- TASK-BE-613 (ADR-007 D1-A) -- per-client branding of the shared login page
-- ============================================================
-- The /login and /signup pages are one form for every OIDC client. ADR-007
-- (ACCEPTED 2026-09-29) lets the INITIATING client's registered settings
-- choose what the page calls itself. This migration gives three clients their
-- values; every other client -- and a direct visit -- shows the default
-- (service IAM, heading "IAM login"), which lives in code (LoginBranding) and
-- needs no row.
--
--   client                          service          title / description
--   platform-console-web            IAM              "IAM login" / "sign in with your operator account"
--   fan-platform-user-flow-client   IAM              "IAM login" / "sign in to IAM securely"
--
-- The fan client was "GAP" in the request; the owner changed it to IAM on
-- 2026-09-29 before this migration shipped ("GAP" is IAM's former name -- V0024
-- renamed the slug). Its row still exists for the subtitle and the colour.
--   ecommerce-web-store-client      Global Account   "sign in with Global Account" / "... to keep shopping"
--
-- (English glosses above; the stored values are Korean and are pinned
--  character-for-character by LoginPageBrandingSeedIntegrationTest.)
--
-- Keys (OAuthClientMapper.SETTING_BRANDING_*), all plain JSON strings:
--   custom.branding.service-name / title / description / logo / primary-color
-- LoginBranding validates on READ (colour must be #RRGGBB, logo must be an
-- allowlisted name) -- a bad value here degrades to the default, it cannot
-- inject anything. The values are display-only: nothing in the authorization,
-- consent, token or redirect path reads them.
--
-- Why JSON_MERGE_PATCH and not string REPLACE like V0028/V0031/V0033/V0035
-- ------------------------------------------------------------
-- Those migrations anchored on a QUOTED URI fragment, which survives the way
-- MySQL prints a JSON column. `client_settings` is a JSON column (V0008), and
-- MySQL re-serialises it on read: a space after every colon and comma, keys
-- re-ordered. An anchor on STRUCTURE (the opening brace, a key followed by a
-- colon) therefore matches nothing, the UPDATE touches 0 rows, and Flyway
-- reports SUCCESS. There is no URI to anchor on here, so REPLACE is the wrong
-- tool. JSON_MERGE_PATCH adds the five keys and leaves every other key --
-- including the type-tagged post-logout array -- untouched.
--
-- The old reason to avoid MySQL JSON functions ("they break the H2 slice
-- tests", V0011/V0028) does not apply to this service's current tests:
-- OAuth2AuthorizationServerSliceTest runs with spring.flyway.enabled=false and
-- builds its schema from db/h2/oauth2-authorization-schema.sql (checked
-- 2026-09-29). Flyway runs on MySQL only.
--
-- ASCII only, and why the escapes are DOUBLED
-- ------------------------------------------------------------
-- Every migration in this directory is pure ASCII (V0035 names the rule), so
-- the Korean values are written as JSON unicode escapes. In a MySQL string
-- literal a backslash before an unknown escape letter is DROPPED, so a single
-- backslash-u would reach the JSON parser as a bare "u" plus four hex digits
-- and be stored as literal text. Written doubled, the SQL literal yields one
-- backslash, and the JSON parser decodes it to the character. (NO_BACKSLASH_
-- ESCAPES would flip this; it is set nowhere in this repo -- grep, 2026-09-29.
-- The integration test compares the decoded strings, so either failure mode
-- goes red there, not in the browser.)
--
-- No dollar-brace sequence appears anywhere in this file: Flyway substitutes
-- placeholders over the whole file, comments included (V0031 died on it twice).
--
-- Idempotency: rows that already carry a branding key are skipped.
-- ============================================================

UPDATE oauth_clients
   SET client_settings = JSON_MERGE_PATCH(client_settings,
           '{"custom.branding.service-name":"IAM",
             "custom.branding.title":"IAM \\uB85C\\uADF8\\uC778",
             "custom.branding.description":"\\uC6B4\\uC601\\uC790 \\uACC4\\uC815\\uC73C\\uB85C \\uB85C\\uADF8\\uC778\\uD569\\uB2C8\\uB2E4",
             "custom.branding.logo":"console",
             "custom.branding.primary-color":"#171717"}'),
       updated_at = NOW()
 WHERE client_id = 'platform-console-web'
   AND JSON_CONTAINS_PATH(client_settings, 'one', '$."custom.branding.service-name"') = 0;

UPDATE oauth_clients
   SET client_settings = JSON_MERGE_PATCH(client_settings,
           '{"custom.branding.service-name":"IAM",
             "custom.branding.title":"IAM \\uB85C\\uADF8\\uC778",
             "custom.branding.description":"IAM\\uC73C\\uB85C \\uC548\\uC804\\uD558\\uAC8C \\uB85C\\uADF8\\uC778\\uD569\\uB2C8\\uB2E4",
             "custom.branding.primary-color":"#9333ea"}'),
       updated_at = NOW()
 WHERE client_id = 'fan-platform-user-flow-client'
   AND JSON_CONTAINS_PATH(client_settings, 'one', '$."custom.branding.service-name"') = 0;

UPDATE oauth_clients
   SET client_settings = JSON_MERGE_PATCH(client_settings,
           '{"custom.branding.service-name":"Global Account",
             "custom.branding.title":"Global Account\\uB85C \\uB85C\\uADF8\\uC778",
             "custom.branding.description":"Global Account\\uB85C \\uB85C\\uADF8\\uC778\\uD558\\uC5EC \\uC1FC\\uD551\\uC744 \\uACC4\\uC18D\\uD558\\uC138\\uC694.",
             "custom.branding.primary-color":"#1a1a2e"}'),
       updated_at = NOW()
 WHERE client_id = 'ecommerce-web-store-client'
   AND JSON_CONTAINS_PATH(client_settings, 'one', '$."custom.branding.service-name"') = 0;
