-- !!! DEV/DEMO ONLY — never reaches production. !!!
-- Loaded via spring.flyway.locations ONLY under the `e2e` profile
-- (application-e2e.yml); application.yml pins the DEFAULT profile — which is what
-- production runs, auth-service has no application-prod.yml — to db/migration alone.
-- The demo stack gets this file through the same `docker-compose.e2e.yml` overlay as
-- its siblings (DemoSeedCredentialTest pins both halves).
--
-- TASK-MONO-781 — the console login of the CS 2nd-line demo operator (`cs@demo.com`).
-- The operator row, why its home tenant is `ecommerce`, why its SUPPORT_LOCK grant is
-- bound to `ecommerce` and the disclosed domain-role trade-off are in admin-service
-- migration-dev R__seed_demo_cs_operator.sql — kept there rather than duplicated here so
-- the two halves cannot drift into two explanations.
--
-- Shape copied from R__seed_demo_viewer_operator_credential.sql, for the same reasons:
--   · REPEATABLE (R__), not the V9000+ band — a high-band dev seed makes every later
--     production migration out-of-order (auth_db crash-looped on exactly that).
--   · tenant `iam` — the `platform-console-web` client's tenant, so the scoped
--     credential lookup HITS and the cross-tenant fallback (fail-closed on ambiguity)
--     is never reached. The email exists in exactly one tenant.
--   · its own email (UNIQUE (tenant_id, email) since V0007) and its own account_id
--     (GLOBAL unique index, V0001). The account_id is the OIDC `sub` and the link key
--     `admin_operators.oidc_subject` must equal LITERALLY; DemoCsOperatorSeedTest
--     compares the two files.
--   · the same published demo password as its siblings, hence the same Argon2id digest;
--     the test re-verifies it with the login path's own hasher.
--
-- No domain roles are seeded (a base operator token carries none). The roles this
-- operator receives at assume time are derived from `ecommerce`'s subscriptions —
-- DemoCsOperatorDerivedRolesTest pins exactly which.
--
-- Idempotent: INSERT IGNORE — which is also what makes R__ safe here.

INSERT IGNORE INTO credentials (
    tenant_id, account_id, email,
    credential_hash, hash_algorithm, created_at, updated_at, version
) VALUES
-- console (CS 2nd-line operator) — client `platform-console-web`, tenant `iam`.
(
    'iam', '0199de70-0000-7000-8000-00000000ad08', 'cs@demo.com',
    '$argon2id$v=16$m=65536,t=3,p=1$NR1Seql5fgXB0hQ7CmpFL6RyiXvL86lxeZCobfiBdRxzRlTkkcv6iIZDJq9eQ32QmKQMylwsG+IP25S1aaw9vw$kTFrCq8cQG4HVUKioosaD88eiXZkQesTp5Xc8yylaSM',
    'argon2id', NOW(6), NOW(6), 0
);
