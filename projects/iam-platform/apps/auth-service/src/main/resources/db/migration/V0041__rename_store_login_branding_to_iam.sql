-- ============================================================
-- TASK-BE-616 (ADR-007 value change 2026-10-01) -- the store's login page says IAM
-- ============================================================
-- ADR-007 "value change (2026-10-01, owner)": one account name. The fan client
-- was already IAM (V0040); the store kept "Global Account" because, until
-- ADR-MONO-078 A, a fan account and a store account were different accounts.
-- With the consumer account pool (TASK-BE-614/615/616) they are one account, so
-- the name must say so. This migration ships in the same change that turns
-- iam.consumer-pool.enabled on (account-service) -- the name says "one account"
-- exactly when it becomes one.
--
--   client                       service   title / description
--   ecommerce-web-store-client   IAM       "IAM login" / "sign in with your IAM account to keep shopping"
--
-- (English gloss; the stored values are Korean and are pinned character for
--  character by LoginPageBrandingSeedIntegrationTest.)
--
-- Only the NAME changes: service-name, title, description. The store's colour
-- (#1a1a2e, V0040) is left alone -- colour and subtitle are what tell the
-- visitor which site they are entering (ADR-007 D2).
--
-- Never edit V0040 (applied migration, Flyway checksum): this is a new version.
--
-- Same technique and the same traps as V0040 -- read its header:
--   * JSON_MERGE_PATCH, not string REPLACE (MySQL re-serialises the JSON column).
--   * ASCII only; Korean as JSON unicode escapes with the backslash DOUBLED
--     (a single one is dropped by the MySQL string literal).
--   * No dollar-brace sequence anywhere (Flyway placeholders, comments included).
--
-- Guard: only a row that still carries V0040's value is rewritten. A row whose
-- branding someone has since changed by hand is left as it is, and a re-run is
-- a no-op (the guard no longer matches).
-- ============================================================

UPDATE oauth_clients
   SET client_settings = JSON_MERGE_PATCH(client_settings,
           '{"custom.branding.service-name":"IAM",
             "custom.branding.title":"IAM \\uB85C\\uADF8\\uC778",
             "custom.branding.description":"\\uC1FC\\uD551\\uC744 \\uACC4\\uC18D\\uD558\\uB824\\uBA74 IAM \\uACC4\\uC815\\uC73C\\uB85C \\uB85C\\uADF8\\uC778\\uD558\\uC138\\uC694."}'),
       updated_at = NOW()
 WHERE client_id = 'ecommerce-web-store-client'
   AND JSON_UNQUOTE(JSON_EXTRACT(client_settings, '$."custom.branding.service-name"')) = 'Global Account';
