# Task ID

TASK-MONO-207

# Title

ADR-MONO-023 § 3.3 D2 cross-service plane-separation federation-e2e proof + federation-e2e harness regression repair. Adds the runtime capstone TASK-BE-344 deferred — a federation-hardening-e2e spec proving an entitlement SUSPEND (entitlement plane) drops the domain from a RE-ISSUED operator token's signed `entitled_domains` while the `operator_tenant_assignment` row (IAM plane) stays byte-unchanged (GCP billing↔IAM parity) — driving the mutation through the real `subscription.manage` admin RBAC surface (D3 → D2 delegation). Also repairs two pre-existing main regressions that left the nightly RED and would block the proof: (1) the gap→iam rename (#1149) jar-restore path mismatch (`artifact-staging/iam/` vs the `iam-platform/` artifact root) and (2) the BE-341 (#1240) production `V0021` Flyway version collision with the dev-seed `V0021` under the e2e profile.

# Status

ready

# Owner

backend

# Task Tags

- e2e
- federation-hardening
- adr
- multi-tenant
- ci

---

# Dependency Markers

- **proves**: ADR-MONO-023 D2 plane-separation invariant — the cross-service half explicitly deferred by TASK-BE-344 ("operator token issuance → suspend → token re-issuance drops the domain from entitled_domains while the operator_tenant_assignment row + RBAC stay byte-unchanged — belongs to the federation-hardening-e2e stack").
- **depends on**: TASK-BE-342 (account-service subscription mutation + event, #1242) + TASK-BE-343 (admin-service `subscription.manage` surface, #1244) — the spec drives that surface; TASK-BE-341 (#1240, the status machine the suspend/resume rides).
- **fixes (regression)**: TASK-MONO-179 / #1149 (gap→iam rename) left the federation-e2e jar-restore source as `artifact-staging/iam/apps/...` while the upload-artifact@v4 root is `iam-platform/apps/...` → `mv` exit 1 → nightly RED since 2026-06-06.
- **fixes (regression, latent)**: TASK-BE-341 / #1240 added production `db/migration/V0021` (status CHECK), colliding with dev `db/migration-dev/V0021` (globex) under the merged e2e Flyway timeline → account-service would fail to start once the jar-restore is fixed.

# Goal

Make the ADR-023 D2 plane-separation invariant executable on the full federation stack: prove, through the real OIDC login → admin RBAC mutation → assume-tenant re-issuance path, that a runtime entitlement suspend is reflected in the re-issued token's signed claims while the IAM binding is untouched and the change is reversible. Restore the nightly federation-hardening-e2e to GREEN (it is the verification channel) by fixing the two regressions that block the stack from even starting.

# Scope

**Regression repair (federation-e2e must start + not collide):**
- `.github/workflows/federation-hardening-e2e.yml` — the 3 IAM jar-restore sources `artifact-staging/iam/apps/...` → `artifact-staging/iam-platform/apps/...` (match the upload-artifact@v4 longest-common-prefix-stripped root). Run-step name count refresh.
- `projects/iam-platform/apps/account-service/src/main/resources/db/migration-dev/V0021__seed_globex_e2e_customer.sql` → **renumber to `V9001`** (`git mv`) — move dev seeds to a high V9000+ band the (gapless) production timeline never reaches, ending the collision with production `V0021`.
- `application-e2e.yml` — document the V9000+ dev-seed band convention + the collision lesson.

**The proof:**
- NEW `projects/iam-platform/apps/account-service/.../db/migration-dev/V9002__seed_initech_e2e_customer.sql` — a DEDICATED tenant `initech-corp` [finance, wms] ACTIVE (present at startup, like globex; isolated so the runtime suspend/resume cannot race-break the fullyParallel acme/globex specs).
- `tests/federation-hardening-e2e/fixtures/seed.sql` § 14 — the multi-operator → `initech-corp` `operator_tenant_assignment` row (the IAM-plane binding the proof shows surviving the suspend).
- NEW `tests/federation-hardening-e2e/specs/subscription-plane-separation.spec.ts` — the proof (below).
- `tests/federation-hardening-e2e/README.md` — post-MVP spec note.

**Spec design:**
- Two contexts: SUPER_ADMIN (owns the mutation — only it has `subscription.manage`) + the multi-operator (observes the re-scoped token). Both fresh OIDC PKCE (no `*` wildcard).
- A. switch to `initech-corp` → decode `console_assumed_token` → `entitled_domains` = [finance, wms].
- B. SUSPEND `finance` via `PATCH /api/admin/subscriptions/initech-corp/finance/status` (the `subscription.manage` surface, Bearer = exchanged operator token).
- C. re-switch (token RE-MINTED, switch still 200 = assignment intact) → `entitled_domains` excludes `finance`, still includes `wms`.
- D. RESUME `finance` → re-switch → both entitled again. `finally` guarantees restoration.

# Acceptance Criteria

- **AC-1** The federation-hardening-e2e jar-restore step succeeds (the 3 IAM jars land at their canonical paths); the stack boots through Phase 2 (regression #1 fixed).
- **AC-2** account-service starts cleanly under the e2e profile with both production `V0021` and the renumbered dev `V9001`/`V9002` (no Flyway version collision — regression #2 fixed).
- **AC-3** The proof spec is GREEN: after a runtime suspend of `initech-corp/finance` via the `subscription.manage` admin surface, the re-issued assumed token's `entitled_domains` drops `finance` and keeps `wms`; resume restores both.
- **AC-4** The tenant switch returns 200 throughout (the `operator_tenant_assignment` row — IAM plane — is intact across the suspend; the assume-tenant D2 assignment gate would 403 otherwise).
- **AC-5** GREEN on the federation-hardening-e2e workflow (nightly / `gh workflow run federation-hardening-e2e.yml`), all specs (the regression repair restores the whole suite, not just this spec).

# Related Specs

- `docs/adr/ADR-MONO-023-entitlement-iam-plane-separation.md` § D2 / D6
- `docs/adr/ADR-MONO-018-platform-console-phase-8-federation-hardening.md` (harness location/scope D1–D4)
- `projects/iam-platform/specs/contracts/http/admin-api.md` § Subscription Management (the surface the spec drives)

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md`
- `projects/iam-platform/specs/contracts/events/account-events.md` (`tenant.subscription.changed`)

# Edge Cases

- `initech-corp` is referenced by NO other spec → the runtime suspend/resume is parallel-safe; `finally` restores `finance` to ACTIVE for re-runnability + the CI `retries: 2`.
- Dev seeds MUST be present at account-service startup (the keystone reverse-lookup only returns Flyway-loaded rows — the MONO-160 lesson) → `initech-corp` is Flyway-dev (V9002), NOT seed.sql; only the admin_db assignment side is in seed.sql.
- The admin surface is called directly at `E2E_ADMIN_BASE_URL` (no console-web subscription proxy route exists, unlike `/api/tenant`) with the exchanged operator token read from the `console_operator_token` HttpOnly cookie — the `/api/admin/**` credential per console-integration-contract § 2.6.
- `X-Operator-Reason` is required by the surface (`ReasonRequiredException` → 400 otherwise); the spec always sends it.
- Baseline/cleanup resume tolerates the 409 self-transition when `finance` is already ACTIVE.

# Failure Scenarios

- If account-service cached `entitled_domains` (it does not — `listActive` is a direct read), the re-issued token would not reflect the suspend and AC-3 would fail.
- If the suspend mutated an IAM binding (it cannot — D2 one-way plane; account-service has no admin_db access), the switch would 403 and AC-4 would fail.
- If a future production account-service migration reuses a V9000+ number, the dev band would collide again — the band is chosen far above the production timeline to prevent this (AC-2 guard).
