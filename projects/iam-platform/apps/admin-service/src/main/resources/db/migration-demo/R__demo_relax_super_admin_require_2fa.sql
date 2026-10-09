-- !!! PORTFOLIO DEMO ONLY. !!!
-- TASK-MONO-771 S4 — owner decision OD-5 (2026-10-08, FINAL): «데모 dev 시드에서만 SUPER_ADMIN require_2fa 완화
-- (e2e 픽스처와 같은 방식) · 데모의 2단계는 테넌트 정책 토글로 보인다».
--
-- WHO LOADS THIS FILE — and who does not (this is the whole point of the separate location)
-- ---------------------------------------------------------------------------
-- `db/migration-demo` is in NO `spring.flyway.locations` of any profile in this service:
--   application.yml (default / local dev)  = db/migration, db/migration-dev   → NOT loaded
--   application-dev.yml                     = (inherits default)               → NOT loaded
--   application-e2e.yml (CI e2e / nightly)  = db/migration, db/migration-dev   → NOT loaded
--   application-prod.yml                    = db/migration                     → NOT loaded
--   src/test application-test.yml           = db/migration, db/migration-dev   → NOT loaded
-- The ONLY thing that adds it is `infra/demo/iam-traefik.override.yml` (admin-service environment
-- `SPRING_FLYWAY_LOCATIONS`) — the demo-only overlay that CI does not render. So the relaxation reaches the
-- portfolio demo and nothing else. `DemoOnlyRequire2faRelaxationTest` pins all of the above.
--
-- WHY NOT IN `db/migration-dev/R__seed_demo_operator.sql` (where the demo operators are seeded)
-- ---------------------------------------------------------------------------
-- admin-service loads `db/migration-dev` in the DEFAULT profile too (TASK-MONO-771 AC-0 § 1 row 2+), i.e.
-- on every developer's local admin_db and in the CI e2e profile. Relaxing there would relax far more than
-- the demo. The console e2e harness relaxes for itself, at runtime, in its own fixture
-- (`projects/platform-console/apps/console-web/tests/e2e/fixtures/seed.sql` § 5) — this file is the same
-- one statement, scoped to the demo the same way that one is scoped to the harness.
--
-- WHAT STAYS ENFORCED IN THE DEMO
-- ---------------------------------------------------------------------------
-- Only the ROLE term goes quiet (both demo operators hold SUPER_ADMIN — R__seed_demo_operator.sql). The
-- tenant entry policy (`tenant_entry_policy`, V0047) is untouched: turning a tenant's policy on in the demo
-- still refuses assume into it without a second factor — that is how the demo shows 2FA (OD-5).
-- SECURITY_ANALYST keeps require_2fa = TRUE.
--
-- Repeatable: Flyway runs R__ after every versioned migration, so this always lands after V0013 set the
-- flag TRUE. Idempotent (UPDATE with WHERE).

UPDATE admin_roles
   SET require_2fa = FALSE
 WHERE name = 'SUPER_ADMIN';
