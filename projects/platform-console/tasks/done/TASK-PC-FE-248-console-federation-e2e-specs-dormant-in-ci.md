# TASK-PC-FE-248 — console federation e2e specs are gated OFF in every CI workflow (dormant coverage)

- **Type**: TASK-PC-FE (frontend e2e / CI wiring)
- **Status**: done
- **Service**: platform-console `console-web` (`tests/e2e/` Playwright suite)
- **Analysis model**: Opus 4.8 · **Impl model**: Opus (CI wiring + demo-stack seeding, decision-gated)

## Goal

The two console-web federation Playwright specs —
`tests/e2e/federation-omni-all-domains.spec.ts` and
`tests/e2e/federation-ecommerce-sellers-multi.spec.ts` — are env-gated on
`PC_FEDERATION_E2E=1` (`fixtures/federation.ts` `shouldSkipFederation()` =
`process.env.PC_FEDERATION_E2E !== '1'`). That flag is set in **zero** workflow
files, so both specs **SKIP in every CI run** (nightly-e2e console job + ci.yml
frontend jobs). Net effect: **`/ecommerce/sellers` and the omni all-5-domain
overview assertion have specs that present as coverage but never execute** — a
dormant-spec gap surfaced by the 2026-07-19 console menu verification sweep.

Decide and implement one of: (a) **enable** them in a CI job that has the
federation stack + seeded personas, or (b) **remove** them if unmaintainable —
so the repo's coverage state is honest (a spec that never runs is worse than no
spec: it reads as green coverage).

## AC-0 — Finding (verified 2026-07-19)

- `grep -r PC_FEDERATION_E2E .github/workflows` → **0 hits** (flag only appears
  in the spec/fixture source). Confirm this holds at start.
- The console-web CI e2e job uses `docker-compose.e2e.yml` (IAM + finance-account
  + console-bff + console-web) which **does NOT contain** the domain backends
  (ecommerce/scm/wms/erp) these specs exercise — so they cannot run in that job
  as-is; they need the full `federation-hardening-e2e` stack.
- The personas (`omni-operator@example.com`, `multi-operator@example.com`) are
  seeded by the committed `fixtures/seed-federation-personas.sql`, which must be
  applied to whatever stack runs them. **NOTE (live evidence):** on an ad-hoc
  federation demo stack these personas were **not** seeded (login `?error`),
  while `e2e-super-admin` was — so any enable path must guarantee the seed runs.

## Scope

- **In**: decide enable-vs-remove (AC-0); then either wire a CI job (bring up the
  federation stack, apply the persona seed, set `PC_FEDERATION_E2E=1`, run the 2
  specs) or delete the 2 specs + `fixtures/federation.ts` + the persona seed and
  document the removal rationale in the nav/e2e README.
- **Out**: the console-web CI e2e job's existing specs (they run + pass); adding
  NEW menu coverage (that is TASK-PC-FE-249).

## Acceptance Criteria (finalize after AC-0)

- **AC-1a (enable)**: a CI job runs both federation specs against a stack that has
  the domain backends + the seeded personas, with `PC_FEDERATION_E2E=1`; both
  pass (or the run surfaces a real defect to fix). `/ecommerce/sellers` +
  omni-overview are then actually exercised in CI.
- **AC-1b (remove)**: the 2 specs + federation fixture + persona seed are deleted;
  the e2e README documents why (the console-web CI stack can't host the domain
  backends); `/ecommerce/sellers` coverage is noted as vitest-only.
- **AC-2**: the honest-coverage invariant holds — no console e2e spec exists that
  is unconditionally skipped in CI.

## Related

- Provenance: 2026-07-19 console menu verification sweep (47-menu matrix; live
  SSR sweep confirmed `/ecommerce/sellers` + omni overview **render fine** — this
  is a regression-coverage gap, NOT a functional defect).
- `nightly-e2e.yml` job `platform-console-e2e-fullstack`; `federation-hardening-e2e.yml`.
- Sibling: `TASK-PC-FE-249` (render coverage for 3 unverified menus + stale nav comments).

## Edge Cases / Failure Scenarios

- Enabling may reveal the specs are stale (assert on old DOM) — treat a first-run
  RED as a real finding to fix, not a reason to re-disable.
- The federation stack is heavy; a dedicated nightly job (not per-push) is the
  likely home so it does not slow the merge gate.
- Removal must not orphan `seed-federation-personas.sql` references elsewhere.
