-- =============================================================================
-- REPEATABLE (R__) — this file was `V9002__…` until TASK-MONO-524 (2026-08-12).
-- =============================================================================
-- The V9000+ band was chosen (TASK-MONO-207) to stop dev seeds COLLIDING with
-- production version numbers. It did that, and in exchange it poisoned ORDERING:
-- once 9001 is the highest APPLIED version, every later production migration
-- resolves BELOW it = out-of-order, which Flyway rejects by default. auth_db hit
-- exactly that when V0032 landed (2026-08-11) and crash-looped on every host
-- with an existing volume. CI never saw it — CI always starts from a fresh
-- volume, where everything applies in order.
--
-- R__ has neither problem: a repeatable carries NO version (nothing to collide
-- with, nothing to be out of order against) and always runs AFTER every
-- versioned migration — which is the order seed data wants anyway.
--
-- Editing rules this buys, all three load-bearing:
--   · it re-runs whenever its checksum changes ⇒ every statement MUST stay
--     idempotent (this file: INSERT IGNORE only)
--   · repeatables run in DESCRIPTION order ⇒ the `NN_` prefix is the ordering
--     contract between the seeds in this directory. Do not drop it.
--   · never rename an already-applied R__ file — the description is its
--     identity, and a rename reads as "applied migration not resolved locally",
--     which Flyway does NOT tolerate. (A missing VERSIONED file it *does*
--     tolerate, silently, as `future` — that asymmetry is measured in the doc.)
--
-- Rationale in full + how to recover an already-poisoned database:
--   projects/iam-platform/docs/flyway-dev-seed-migrations.md
-- =============================================================================
-- TASK-MONO-207 (ADR-MONO-023 D2 cross-service plane-separation federation-e2e
-- proof): seed a THIRD demo customer `initech-corp`, dedicated to the
-- subscription-plane-separation spec, with TWO ACTIVE subscriptions
-- [finance, wms].
--
-- WHY a dedicated tenant (not acme-corp / globex-corp): the proof spec mutates a
-- subscription AT RUNTIME (suspend → resume) via the admin RBAC surface. The
-- federation-e2e suite runs fullyParallel; acme-corp [finance,wms] and
-- globex-corp [scm,erp] are asserted by the MONO-154 entitlement-trust and
-- MONO-158 A↔B switch specs, so a runtime suspend on either would race-break
-- them. `initech-corp` is referenced by NO other spec — the suspend/resume cycle
-- is fully isolated. `finance` is the suspend target; `wms` is the control that
-- must stay entitled (proving only the targeted entitlement drops).
--
-- WHY migration-dev V9000+ (not seed.sql, not production db/migration): identical
-- rationale to V9001 (globex) — account-service's keystone reverse-lookup only
-- returns rows present at startup via Flyway, and dev-only seeds live in the high
-- V9000+ band to never collide with the production timeline. `db/migration-dev`
-- is loaded ONLY under the e2e Flyway locations (application-e2e.yml) —
-- initech-corp never reaches a production DB.
--
-- The matching admin_db side (the multi-operator → initech-corp
-- operator_tenant_assignment row that lets the operator assume this tenant) is
-- seeded in tests/federation-hardening-e2e/fixtures/seed.sql § 14.
--
-- FK (fk_tds_tenant): the tenant INSERT must precede the subscription INSERTs.
-- INSERT IGNORE keeps it idempotent (mirrors V0014–V0020 + V9001).

INSERT IGNORE INTO tenants (tenant_id, display_name, tenant_type, status, created_at, updated_at)
VALUES ('initech-corp', 'Initech Corporation', 'B2B_ENTERPRISE', 'ACTIVE', NOW(6), NOW(6));

INSERT IGNORE INTO tenant_domain_subscription (tenant_id, domain_key, status, created_at, updated_at)
VALUES ('initech-corp', 'finance', 'ACTIVE', NOW(6), NOW(6)),
       ('initech-corp', 'wms', 'ACTIVE', NOW(6), NOW(6));
