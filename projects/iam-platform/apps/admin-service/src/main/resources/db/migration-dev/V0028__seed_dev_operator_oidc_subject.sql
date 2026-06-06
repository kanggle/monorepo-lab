-- !!! DEV/LOCAL ONLY — DO NOT run in production. Loaded via
-- spring.flyway.locations only in non-prod profiles (application.yml
-- locations = db/migration,db/migration-dev; application-prod.yml restricts
-- to db/migration only). !!!
--
-- TASK-BE-298 / ADR-MONO-014 — provision an oidc_subject for the dev
-- SUPER_ADMIN operators so POST /api/admin/auth/token-exchange works end to
-- end in local/dev (a GAP OIDC platform-console-web token whose `sub`
-- (account_id) equals this value exchanges for an operator token).
--
-- PRODUCTION REMAINS FAIL-CLOSED: in production oidc_subject stays NULL for
-- every operator until an explicit operator-provisioning path sets it
-- (data-model.md §OIDC Subject <-> Operator Link Key). This dev seed never
-- runs in prod. The values are arbitrary placeholder UUIDs; a local operator
-- wiring console-web sets OIDC_CONSOLE_CLIENT_ID + a matching account `sub`.
--
-- Idempotent + NULL-safe: only sets the column when it is still NULL, so
-- re-runs / already-provisioned rows are a no-op. No cross-statement state.

UPDATE admin_operators
   SET oidc_subject = '00000000-0000-7000-8000-0000000devOID'
 WHERE operator_id  = '00000000-0000-7000-8000-00000000dev1'
   AND oidc_subject IS NULL;

UPDATE admin_operators
   SET oidc_subject = '00000000-0000-7000-8000-0000adminOIDC'
 WHERE operator_id  = 'admin'
   AND oidc_subject IS NULL;
