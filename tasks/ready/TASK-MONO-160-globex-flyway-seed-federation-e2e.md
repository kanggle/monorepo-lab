# Task ID

TASK-MONO-160

# Title

Fix-forward for TASK-MONO-158 (ADR-MONO-020 D4) federation-e2e **B-side** — seed the e2e demo customer `globex-corp` ([scm,erp]) via account-service Flyway `db/migration-dev/V0021` (loaded by the `e2e` profile only) instead of the runtime `seed.sql`, so the assume-tenant `entitled_domains` for globex resolves correctly and the A↔B switch proof flips. Root cause (instrumented): account-service's per-tenant keystone query returned **empty** for globex rows inserted post-startup by the external `seed.sql`, while the Flyway-inserted `acme-corp` (V0020) worked through the identical query.

# Status

ready

# Owner

backend

# Task Tags

- code
- e2e

---

# Dependency Markers

- **fixes**: TASK-MONO-158 federation-e2e `tenant-switch-rescope.spec.ts` B-side (run 26713592605 + diagnostic run 26714417091). A-side (switch to acme-corp) PASSED; B-side (switch to globex-corp) deterministically failed — scm/erp stayed gate-rejected because the globex assumed token carried **no** `entitled_domains`.
- **root cause (instrumented, run 26714417091)**: the diagnostic confirmed (a) the globex `tenants` + `tenant_domain_subscription [scm,erp]` rows WERE present + ACTIVE in `account_db`; (b) the auth-service customizer injected `entitled_domains=[finance,wms]` for the acme assume-tenant but injected **nothing** for the globex assume-tenant (empty result, not an exception — no fail-soft WARN). So account-service's `findByStatusAndTenantId(ACTIVE,'globex-corp')` returned empty for the **externally seed.sql-inserted** globex rows, while the **Flyway-inserted** acme-corp (V0020) returned `[finance,wms]` through the identical query — a startup-vs-post-startup data-visibility asymmetry in the e2e stack.
- **also-merged-then-reverted**: the temporary diagnostic (DB-dump workflow step + `--show-warnings` + auth DEBUG logging, PR #987) is reverted by this task.
- **model**: 분석=Opus 4.8 / 구현=Opus (e2e infra; direct fix).

---

# Goal

Make the MONO-158 A↔B federation e2e GREEN by seeding globex-corp the **same way acme-corp works** — via account-service Flyway, present at startup — eliminating the post-startup external-insert variable that caused globex's keystone `entitled_domains` to come back empty.

# Scope

## In scope

1. **`account-service/src/main/resources/db/migration-dev/V0021__seed_globex_e2e_customer.sql`** (new) — `globex-corp` tenant (B2B_ENTERPRISE, ACTIVE) + `tenant_domain_subscription [scm,erp]` (FK-correct order; INSERT IGNORE). Mirrors acme-corp V0020. `migration-dev` is e2e-only.
2. **`account-service/src/main/resources/application-e2e.yml`** — `spring.flyway.locations: classpath:db/migration,classpath:db/migration-dev` (load the dev migration under the `e2e` profile only — production stays `classpath:db/migration`, so globex-corp never reaches a real DB).
3. **`tests/federation-hardening-e2e/fixtures/seed.sql`** — remove the globex `account_db` section (§9); replace with a pointer comment to V0021. The multi-operator (`auth_db`/`admin_db`, §10–13) STAYS in seed.sql (those are admin/auth tables; the assignment + login work — assume_tenant_ok was logged for both tenants).
4. **Revert the MONO-158-B diagnostic** (PR #987): auth-service `application-e2e.yml` DEBUG logging + the workflow DB-dump step + `--show-warnings`.

## Out of scope

- console-web / BFF / domain / the D2 producer — unchanged (MONO-158/159 are correct; the console A-side proves the mechanism).
- Making globex-corp a production migration (it's a demo customer; migration-dev keeps it e2e-only).

# Acceptance Criteria

- **AC-1**: account-service applies V0021 under the `e2e` profile at startup; globex-corp + [scm,erp] present before the producers/console start. GAP Integration (Testcontainers) GREEN (V0021 not loaded there — `db/migration` only — so no IT impact; account-service still boots).
- **AC-2 (the proof)**: re-running `federation-hardening-e2e.yml` post-merge → **SUCCESS** incl. `tenant-switch-rescope.spec.ts` — switch to acme-corp → finance/wms entitled + scm/erp forbidden; switch to globex-corp → the **inverse** (scm/erp entitled + finance/wms forbidden). The other 7 specs stay GREEN (entitlement-trust may show as Playwright-`flaky` retry — acme finance card — but passes).
- **AC-3 (scope-lock)**: changes = the new V0021 + account `application-e2e.yml` + seed.sql §9 removal + the #987 diagnostic revert only.

# Related Specs / Code

- `account-service/.../db/migration/V0020__seed_acme_corp_customer_tenant.sql` (the working Flyway pattern mirrored) ; `V0019__create_tenant_domain_subscription.sql` (schema) ; `TenantDomainSubscriptionQueryUseCase` + `TenantDomainSubscriptionRepositoryImpl.findActiveByTenantId` (the keystone query) ; `auth-service/.../TenantClaimTokenCustomizer.populateEntitledDomains` (the consumer).
- ADR-MONO-020 § 2 D2/D3 (the assume-tenant `entitled_domains` this fixes).

# Edge Cases / Failure Scenarios

- **migration-dev e2e-only**: production Flyway locations unchanged → globex-corp never in a real DB. Other GAP services / the Testcontainers IT use `db/migration` (no dev) → V0021 not applied there.
- **version ordering**: V0021 > V0020 (prod max); Flyway applies it after the prod chain across both locations — no collision.
- **meta (the lesson)**: in this e2e stack, account-service does NOT reliably serve per-tenant keystone reads for rows inserted into account_db AFTER it started (external `seed.sql`); rows inserted via its OWN Flyway at startup work. **Any future e2e customer/subscription that the keystone reverse-looks-up must be Flyway-seeded (migration or migration-dev), not added to the runtime seed.sql.** (The runtime seed.sql remains fine for auth_db/admin_db operator rows, which different code paths read.)

# Notes

- Fix-forward sibling of TASK-MONO-159 (auth→admin wiring) — together they unblock the MONO-158 A↔B proof. federation-e2e is workflow_dispatch/nightly (not PR-gated); AC-2 verified post-merge via `gh workflow run federation-hardening-e2e.yml`.
