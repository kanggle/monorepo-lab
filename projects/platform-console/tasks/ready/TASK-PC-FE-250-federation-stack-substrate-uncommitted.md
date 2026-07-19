# TASK-PC-FE-250 — the federation e2e stack's ecommerce + omni substrate is uncommitted (capability gap + doc drift)

- **Type**: TASK-PC-FE (CI stack / e2e capability — decision-gated)
- **Status**: ready
- **Service**: root `tests/federation-hardening-e2e/` stack + `platform-console` console-web
- **Analysis model**: Opus 4.8 · **Impl model**: Opus (stack sizing + seed authoring; runner-capacity risk)

## Goal

TASK-PC-FE-248 deleted console-web's two federation-gated specs because their runtime
substrate is **intentionally gitignored**, not merely unwired. This ticket owns what that
removal left behind: (a) the real coverage gap, and (b) the documentation that still
describes the uncommitted parts as if they were repo assets.

## AC-0 — Findings carried over (verified 2026-07-20, PC-FE-248)

Re-verify at start; **the code wins over these sentences** (they are a hypothesis, not a source).

1. `tests/federation-hardening-e2e/.gitignore` excludes:
   - `docker/docker-compose.federation-e2e.ecommerce.yml` + `.ecommerce-extra.yml`
     (~21 containers per `docker/HOST-PORTS.md`), `.erp-fullstack.yml`, `.ledger.yml`
   - `fixtures/seed-omni-*.sql`, `seed-ecommerce-*.sql`, `seed-erp-*.sql`, `seed-ledger.sql`
2. `git ls-files docker/` tracks only base + `.demo` + `.replenishment` + `.inbound-expected`.
   `omni-corp` appears **nowhere** in the committed `fixtures/seed.sql`.
3. Consequence: **no CI stack runs an ecommerce backend**, so no spec anywhere asserts the
   `ecommerce` overview card or `/ecommerce/sellers` in a browser.

## Scope / Acceptance Criteria

- **AC-1 (decide, evidence-first)**: decide whether an ecommerce-inclusive federation stack
  is viable **on a GitHub-hosted runner** before building it. The nightly job already runs
  ~14 services in a 50-min budget; the ecommerce overlay adds ~21 containers (8× postgres +
  kafka + minio + redis + 9 services). Memory `env_ecommerce_mass_redeploy_oom_docker_hang`
  records mass ecommerce startup OOM-ing a 32 GB dev host — a 2-core/16 GB runner is the
  binding constraint, so **measure before committing to the shape** (a throwaway
  `workflow_dispatch` branch that only boots the stack and reports `docker stats` is the
  cheap probe). If it does not fit, say so and close AC-2/AC-3 as WONTFIX with the numbers.
- **AC-2 (if viable)**: commit the ecommerce overlay + an `seed-omni-*.sql` equivalent, wire
  them into `federation-hardening-e2e.yml`, and land a spec that asserts the `ecommerce`
  overview card is entitled under `omni-corp` — i.e. restore what PC-FE-248 deleted, in the
  suite that actually has the stack (`tests/federation-hardening-e2e/specs/`), not in
  console-web's suite.
- **AC-3 (doc drift, do this regardless of AC-1's outcome)**: `docker/HOST-PORTS.md`
  § "Verified collision-free" presents the full overlay set (`base` + `.demo` + `.ledger` +
  `.erp-fullstack` + `.replenishment` + `.ecommerce` + `.ecommerce-extra`) as the repo's
  stack, with no indication that 4 of those 7 files are gitignored. Mark which overlays are
  committed vs local-only so a reader cannot conclude the CI stack includes ecommerce.
- **AC-4 (title-vs-body drift)**: `specs/operator-overview-composition.spec.ts` is named
  `renders 5-card grid with all 5 domains showing ok status` but asserts only URL +
  page-title + first heading (MVP-relaxed, TASK-MONO-140 cycle 5). Either rename it to what
  it checks or restore the card assertions. A test title that overstates its body is the
  same honest-coverage defect PC-FE-248 was filed for, one layer down.

## Related

- Provenance: **TASK-PC-FE-248** (removal + `console-web/tests/e2e/README.md` rationale).
- `tests/federation-hardening-e2e/.gitignore`, `docker/HOST-PORTS.md`,
  `.github/workflows/federation-hardening-e2e.yml`.
- Sibling: `TASK-PC-FE-249` (console menu render coverage).
- Memory: `env_ecommerce_mass_redeploy_oom_docker_hang`, `project_guard_reachability_not_just_bite`,
  `env_guard_calibrated_on_laptop_fails_on_runner`, `project_nightly_only_spec_merges_green_then_main_reds`.

## Edge Cases / Failure Scenarios

- The overlay may be uncommitted **deliberately** (memory `env_console_demo_local_redeploy`
  treats these as local demo assets). Committing it changes that policy — confirm the intent
  rather than assuming the gitignore is an oversight.
- A stack that boots but is starved produces flaky nightly RED, which is worse than the
  current honest gap. Prefer WONTFIX-with-numbers over a stack that only sometimes fits.
- Re-adding a spec must not reintroduce an env-gate in `console-web/tests/e2e/` — that
  directory's honest-coverage invariant (its README) forbids it.
