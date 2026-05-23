# Task ID

TASK-PC-FE-029

# Title

`/operators` page e2e rendering — investigate + fix why `getByTestId('my-profile-default-account-id')` is not visible within 5s after the BE-311 chain restored end-to-end OIDC PKCE login. TASK-BE-311 iter 7 (dispatch run `26332803785`) advanced the failure boundary from auth-service `/login` form rendering into the operator spec UI assertion: `operators-profile.spec.ts:44` expects `MyProfileForm`'s `my-profile-default-account-id` input testid to be visible, but the 5s wait times out. The page's `OperatorsPage` server component renders one of FOUR mutually-exclusive states (`noTenant` / `permissionError` / `degraded || !state.page` / OK → OperatorsScreen+MyProfileForm); the fixture seeds `console_active_tenant=fan-platform` so `noTenant` is excluded. Investigation must determine which of the remaining 3 states the deployment lands in and what producer-side condition triggers it.

# Status

ready

# Owner

frontend

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- fix

---

# Dependency Markers

- **depends on**: TASK-BE-311 (close `893b2a51` — auth-service /login form rendering chain complete; OIDC PKCE flow now reaches `(console)/dashboards` landing). TASK-PC-FE-028 (close `1892ab52` — DNS layer cleared via `/etc/hosts`).
- **prerequisite of**: nightly main GREEN restoration. AC-2 of PC-FE-028 + AC-1 of BE-311 still deferred to this task's closure.

---

# Goal

Make the next dispatch / push `Platform Console E2E full-stack` job's step 17 `Run Playwright e2e (2 specs)` SUCCESS (or surface the NEXT layer per cycle pattern). After this fix lands, `page.goto('/operators')` followed by `page.getByTestId('my-profile-default-account-id')` resolves to a visible input within Playwright's default 5s timeout, the spec proceeds through fill/save/success assertions, and the full operator self-serve regression invariant runs to completion.

## Root cause evidence (BE-311 iter 7 dispatch `26332803785`)

- **Playwright failure** (`operators-profile.spec.ts:44`):

  ```
  Error: expect(locator).toBeVisible()
    Locator: getByTestId('my-profile-default-account-id')
    Expected: visible
    Timeout: 5000ms
    Error: element(s) not found

    Call log:
      - Expect "toBeVisible" with timeout 5000ms
      - waiting for getByTestId('my-profile-default-account-id')

       44 |     await expect(input).toBeVisible();
          |                         ^
  ```

- **OIDC PKCE login flow PRECEDING this failure is COMPLETE**:
  - trace.network URL chain (BE-311 iter 7) reaches `localhost:3000/dashboards → 200` after the `(console)` layout `isAuthenticated()` guard passes.
  - `console-web` log shows `oidc_login_success` + `operator_exchange_ok` (i.e., GAP access token + RFC 8693 operator token both issued).
  - globalSetup's storage-state was persisted; the spec inherits the authenticated context.

- **Significance**: the failure is INSIDE the authenticated spec body (not globalSetup). The browser is logged in, has all 3 session cookies (`console_access_token` / `console_refresh_token` / `console_operator_token`) + `console_active_tenant=fan-platform`, and navigates to `/operators`. The server-rendered `OperatorsPage` then renders one of four states (page.tsx lines 36-145). The `my-profile-default-account-id` testid lives in `MyProfileForm`, which is mounted ONLY in the "OK" branch (`OperatorsScreen`).

## Hypothesis pool (to narrow during impl)

The fixture seeds `console_active_tenant=fan-platform` BEFORE the spec runs, so `state.noTenant` is excluded. The remaining 3 candidate states + their producer-side triggers:

1. **`state.permissionError.code === 'PERMISSION_DENIED'`** — the operator's principal returned by admin-service does not carry `operator.manage` capability. Investigation: admin-service `/api/admin/operators` returns `403 PERMISSION_DENIED`. Root cause candidates:
   - The seed.sql operator row's `roles` / `capabilities` column does not include `operator.manage` or `SUPER_ADMIN`. 
   - admin-service operator-token-claim mapping does not project the seed row's roles correctly.

2. **`state.permissionError.code === 'TENANT_SCOPE_DENIED'`** — the operator's tenant scope does not include `fan-platform`. Investigation: admin-service `/api/admin/operators?tenant=fan-platform` returns `403 TENANT_SCOPE_DENIED`. Root cause candidates:
   - Seed.sql operator row's `tenants` column omits `fan-platform` (only `*` for platform-scope or some other value).
   - Default tenant cookie seeded by fixture mismatches operator's tenant scope.

3. **`state.degraded || !state.page`** — admin-service `/api/admin/operators` returned `5xx` or timed out, OR the response shape did not parse into a usable page. Investigation: console-web log includes `oidc_admin_operators_call_failed` or similar; admin-service log shows 5xx exception OR a contract drift between admin-service response shape and console-web zod schema.

4. **Hidden 4th case** — the OperatorsScreen renders but MyProfileForm itself fails to mount (e.g., a feature flag, a parent component throwing). Less likely given the unconditional render in page.tsx lines 133-146, but the diagnostic step should capture the actual rendered HTML to rule out.

## Decision authority — defer

Spec does not pre-select an implementation option. Mirroring TASK-BE-311's investigation-first pattern (which converged from a 5-hypothesis pool to root cause in a single ~14-min dispatch via a diagnostic log emission):

The impl PR's first commit adds a focused diagnostic at the `/operators` server component:
- Log the resolved `state` shape (`noTenant` / `permissionError.code` / `degraded` / OK) at server-render time, with logger name `TASK-PC-FE-029`.
- This single log line in the auth-service compose log dump will identify which of hypotheses 1-3 applies (or eliminate them, surfacing the 4th case).

Subsequent commits apply the targeted fix (most likely a seed.sql update for hypothesis 1/2, or an admin-service config / response shape fix for hypothesis 3). Diagnostic cleanup follows in the final commit.

---

# Scope

## In Scope

- `projects/platform-console/apps/console-web/src/app/(console)/operators/page.tsx` — add diagnostic log (iter 1); remove in cleanup iter.
- Whichever of these the diagnostic narrows to:
  - `projects/platform-console/apps/console-web/tests/e2e/fixtures/seed.sql` — operator row roles/tenants/capabilities (hypotheses 1/2).
  - `projects/global-account-platform/apps/admin-service/src/...` — operator-token-claim mapping, operator list query (hypotheses 1/2/3).
  - `projects/platform-console/apps/console-web/src/features/operators/api/operators-api.ts` — response shape / zod schema drift (hypothesis 3).
- This task md + project `tasks/INDEX.md` ready entry.

## Out of Scope

- TASK-BE-311's auth-service fixes (closed; do not re-touch).
- TASK-PC-FE-028's docker-compose / workflow / fixture bridge changes (closed; do not re-touch).
- The second e2e spec `operators-admin-profile.spec.ts` (it runs AFTER `operators-profile.spec.ts`; once this task's hypothesis is fixed, the second spec's any-remaining failure becomes a separate cycle layer).

---

# Acceptance Criteria

- [ ] **AC-1 (functional, primary)** — Next dispatch / push `Platform Console E2E full-stack` job's step 17 `Run Playwright e2e (2 specs)` **SUCCESS** (full job GREEN). Verified by `gh run view <id>` step 17 conclusion = success + overall conclusion = success.
- [ ] **AC-2 (functional, secondary)** — `operators-profile.spec.ts` passes all 5 assertion lines (input visible → fill → save → success → dashboards/overview finance card 'ok').
- [ ] **AC-3 (functional, tertiary)** — `operators-admin-profile.spec.ts` either passes (full GREEN) OR surfaces a DIFFERENT error class that's NOT a regression of this task's fix. Name the next-cycle task in the close chore narrative if (b).
- [ ] **AC-4 (hard invariant — auth-service byte-unchanged)** — `git diff --stat origin/main -- projects/global-account-platform/apps/auth-service/` = empty (BE-311 fixes stay closed; this task's scope is operator-management area, not the auth layer).
- [ ] **AC-5 (hard invariant — console-bff byte-unchanged)** — `git diff --stat origin/main -- projects/platform-console/apps/console-bff/` = empty (ADR-MONO-017 D4 HARD INVARIANT preserved; **25회째 zero-retrofit invariant**).
- [ ] **AC-6 (hard invariant — workflow + docker-compose byte-unchanged)** — `git diff --stat origin/main -- .github/workflows/ projects/platform-console/docker-compose.e2e.yml` = empty.
- [ ] **AC-7 (hard invariant — 5 other producers byte-unchanged)** — `git diff --stat origin/main -- 'projects/wms-platform/' 'projects/scm-platform/' 'projects/erp-platform/' 'projects/fan-platform/' 'projects/ecommerce-microservices-platform/' 'projects/finance-platform/'` = empty.
- [ ] **AC-8 (diagnostic instrumentation cleanup, if added)** — if hypothesis-narrowing diagnostic instrumentation was added to a first commit, it is removed (or demoted to behind a `@Profile("diagnostic")` / `process.env.E2E_DIAG` gate) before merge. `git grep -n 'TASK-PC-FE-029.*diagnostic' projects/platform-console/apps/console-web/src/` returns 0 lines.
- [ ] **AC-9 (slice test / IT regression check)** — if the fix touches `(console)/operators/page.tsx` rendering logic or `features/operators/api/operators-api.ts` schema, the corresponding vitest unit / slice test continues to pass. Verified by push CI `Frontend unit tests` + `Frontend lint & build` jobs GREEN.
- [ ] **AC-10 (BE-303 3-dim objective merge verification)** — close-chore PR authored only after the impl PR's 3-dim verification passes.

---

# Related Specs

> Before reading Related Specs: follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` + `rules/domains/saas.md` + `rules/traits/{multi-tenant,integration-heavy,audit-heavy}.md`.

- [`projects/platform-console/PROJECT.md`](../../PROJECT.md).
- [`projects/platform-console/apps/console-web/tests/e2e/operators-profile.spec.ts`](../../apps/console-web/tests/e2e/operators-profile.spec.ts) — failing spec at line 44.
- [`projects/platform-console/apps/console-web/src/app/(console)/operators/page.tsx`](../../apps/console-web/src/app/(console)/operators/page.tsx) — server component with 4-state branching.
- [`projects/platform-console/apps/console-web/src/features/operators/components/MyProfileForm.tsx`](../../apps/console-web/src/features/operators/components/MyProfileForm.tsx) — owner of the `my-profile-default-account-id` testid.
- [`projects/platform-console/apps/console-web/src/features/operators/components/OperatorsScreen.tsx`](../../apps/console-web/src/features/operators/components/OperatorsScreen.tsx) — renders MyProfileForm unconditionally; only reached in the "OK" branch.
- [`projects/platform-console/apps/console-web/tests/e2e/fixtures/seed.sql`](../../apps/console-web/tests/e2e/fixtures/seed.sql) — operator seed row (roles, tenants, capabilities).
- [`projects/global-account-platform/specs/services/admin-service/admin-api.md`](../../../global-account-platform/specs/services/admin-service/admin-api.md) — admin-service operators endpoints contract.
- [`projects/platform-console/tasks/done/TASK-PC-FE-028-chromium-host-resolver-rules.md`](../done/TASK-PC-FE-028-chromium-host-resolver-rules.md) + [`projects/global-account-platform/tasks/done/TASK-BE-311-auth-service-e2e-login-form-rendering.md`](../../../global-account-platform/tasks/done/TASK-BE-311-auth-service-e2e-login-form-rendering.md) — predecessor closure narratives.

# Related Contracts

- `projects/global-account-platform/specs/services/admin-service/admin-api.md` — verify `/api/admin/operators` query semantics match console-web's call.

# Related Skills

- None additional.

---

# Edge Cases

- **Seed-side fix vs producer-side fix** — if hypothesis 1/2 lands, the seed.sql row may need an additional `roles` / `tenants` column update, OR the admin-service operator-token-claim mapping may need an explicit `SUPER_ADMIN` → `operator.manage` derivation. Choose the smaller scope (seed if possible; impacts e2e only).
- **Response shape contract drift (hypothesis 3)** — if admin-service response changed shape and console-web zod schema didn't track, the fix may be the schema (consumer-side) or the response (producer-side, AC-4 violation). Investigation must look at both producer + consumer for the divergence direction.
- **Hidden 4th case** — if diagnostic logs `state.page` is non-null and OperatorsScreen renders but MyProfileForm itself fails to mount, dump the actual HTML at the test-result level (Playwright screenshot + outerHTML) to identify the rendering exception. Likely a feature-level bug in MyProfileForm's hook logic.

# Failure Scenarios

- **AC-1 PASS but operators-admin-profile.spec.ts FAILS** — means this task's fix unblocked the first spec but a sibling 2nd-spec issue surfaced. AC-3 (b) accepts this; name the next-cycle task in close chore.
- **AC-1 still fails with a DIFFERENT error class** — 11th cycle layer surfaced (e.g., MyProfileForm rendering issue, navigation issue between /operators and /dashboards/overview). Author next-cycle task.
- **AC-4 / AC-5 violation needed for fix** — if root cause forces auth-service / console-bff change, surface explicitly in close chore as honest scope adjustment with evidence link to the dispatch that disproved the in-scope hypothesis.

---

# Test Requirements

- Existing vitest unit + slice tests for operators page / MyProfileForm continue to pass (push CI).
- AC-1 verification = `workflow_dispatch` on impl branch (≤30 min signal) or post-merge nightly push.

---

# Definition of Done

- [ ] Three PRs landed in order: (1) spec PR (this task md + project `tasks/INDEX.md` ready entry), (2) impl PR (diagnostic + targeted fix + cleanup), (3) close-chore PR with BE-303 3-dim + AC-1 dispatch verify result.
- [ ] AC-1 through AC-10 all checked off in the close-chore PR description.
- [ ] If AC-1 still fails with a different error class, name the next-cycle task in close chore narrative.

---

# 메타 (intended)

① **10th cycle layer in TASK-MONO-014 chain** — PC-FE-023 → 024 → MONO-132 → 025 → 026 → MONO-133 → PC-FE-027 → PC-FE-028 (DNS + 7-iter sub-cycle) → BE-311 (form-rendering + 7-iter sub-cycle through 6 downstream layers) → **TASK-PC-FE-029 (this — spec UI assertion 10th layer)**. Cycle pattern's progressive-surface principle continues at its designed scale.

② **Investigation-first cycle pattern continues to operate** — BE-311 closed with AC-1(b) clause (DIFFERENT error class after the auth-service fix). This task picks up where BE-311 left off, using the same diagnostic-first → targeted-fix → cleanup pattern. Mirrors PC-FE-027 → PC-FE-028 and BE-311 iter 1-8 progression.

③ **Cycle pattern realization** — 10 layers across 4 days (2026-05-21 → 24); each layer narrowing scope (typically 1-3 file mods for the actual fix, plus diagnostic + cleanup commits). Mega-PR alternative would not have been feasible (each layer required evidence from the prior fix landing).

④ **`OperatorsPage` 4-state branching as architecture seam** — the page deliberately renders one of four mutually-exclusive states. The fixture seeds `console_active_tenant=fan-platform` so state 1 is excluded; the diagnostic must surface which of states 2-4 applies in the e2e deployment.

⑤ **25회째 zero-retrofit invariant** — fix lives entirely within `projects/platform-console/` (or, if hypothesis points to admin-service, within `projects/global-account-platform/` with explicit honest scope adjustment for AC-4). ADR-MONO-017 D4 HARD INVARIANT continued.

⑥ **memory update post-cycle** — after PC-FE-029 close, audit-memory cycle to capture: layer 10 closure (operator self-serve regression invariant), `OperatorsPage` 4-state diagnostic pattern (production-deployment-vs-test-profile divergence in operator-token claim mapping or seed data), cycle pattern 10+ layer realization (sustained across 4 days).

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (diagnostic-first impl pattern; first commit = server-component diagnostic log, second commit = targeted fix after evidence narrowing).
