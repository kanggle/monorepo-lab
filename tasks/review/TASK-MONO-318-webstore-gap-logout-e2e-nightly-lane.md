# Task ID

TASK-MONO-318

# Title

Activate the web-store GAP RP-initiated-logout E2E in nightly CI (add the `web-store-iam-logout-e2e` job from the TASK-INT-023 handoff)

# Status

review

# Owner

devops

# Task Tags

- ci
- test

---

# Goal

TASK-INT-023 merged the RP-initiated logout (OIDC `end_session`) spec
(`rp-initiated-logout.spec.ts`), the lean GAP stack (`docker-compose.iam-e2e.yml`),
the consumer seed, and a maintainer handoff (`apps/web-store/e2e/CI-IAM-E2E-HANDOFF.md`)
with the CI job to run it. The job was never added — the spec is gated by
`SKIP_GAP_E2E` and the existing nightly `frontend-e2e-fullstack` keeps it at `1`, so
the logout AC stays **uncovered in CI**. The 2026-06-30 discovery sweep confirmed no
follow-up task exists.

This task adds the `web-store-iam-logout-e2e` job to `.github/workflows/nightly-e2e.yml`
so RP-initiated logout is gated nightly.

# Scope

## In Scope

- Add a new **additive** nightly job (sibling of `frontend-e2e-fullstack`) that boots
  the lean GAP stack, seeds the CONSUMER credential, builds web-store, and runs
  `rp-initiated-logout.spec.ts` with `SKIP_GAP_E2E=0`.
- Adapt the handoff YAML to the repo's conventions: pnpm `9.15.0` + Node `20` +
  `cache-dependency-path`, `if: github.repository == 'kanggle/monorepo-lab'`, no
  `needs: changes` (nightly has no path-filter job), and the missing
  `pnpm --filter web-store build` step (the Playwright `webServer` runs `pnpm start`).

## Out of Scope

- The existing `frontend-e2e-fullstack` job (stays `SKIP_GAP_E2E=1`).
- Un-skipping the 4 GAP CRUD specs (golden-flow/cart/wishlist/account-type-guard) —
  they need the full ecommerce backend (deferred per TASK-MONO-014).
- The spec / compose / seed / helper (already merged by TASK-INT-023).

---

# Acceptance Criteria

- [ ] **AC-1** — `web-store-iam-logout-e2e` job added to `nightly-e2e.yml`; additive
  (does not modify `frontend-e2e-fullstack`).
- [ ] **AC-2** — Job boots the lean GAP stack (`docker-compose.iam-e2e.yml`), waits for
  `auth-service` healthy, seeds the CONSUMER credential, builds web-store, and runs
  `rp-initiated-logout.spec.ts` with `SKIP_GAP_E2E=0`.
- [ ] **AC-3** — Repo-scoped (`if: github.repository == 'kanggle/monorepo-lab'`) and
  YAML-valid.
- [ ] **AC-4** — First nightly run (or a manual `workflow_dispatch`) is GREEN; the
  build-step env wiring confirmed on the Linux runner.

> **Verification note**: this is a **nightly CI job that cannot be run on a local
> Windows host** (needs a Linux runner + the GAP docker stack; the web-store prod
> build needs symlink perms unavailable on Windows — local verification used
> `next dev`). The spec itself was verified locally against a real GAP
> (`1 passed (51.0s)`, per the handoff). AC-4 (the runner-side build-env wiring) is
> confirmed on the first nightly / `workflow_dispatch`, not locally. The job is
> additive + non-blocking, so a first-run env tweak does not affect main or PRs.

---

# Related Specs

- `projects/ecommerce-microservices-platform/apps/web-store/e2e/CI-IAM-E2E-HANDOFF.md` (the source YAML)
- TASK-INT-023 (merged the spec + stack)

# Related Contracts

- 없음 (CI/test infra only).

---

# Edge Cases

- The Playwright `webServer` runs `pnpm start` → needs a prior `pnpm --filter web-store build`.
- The issuer hostname `auth-service` must resolve on the runner (`/etc/hosts`) for both
  the browser and the Next.js server (the PC-FE-028 constraint).

# Failure Scenarios

- Missing build step → `pnpm start` serves a stale/absent build → spec fails. AC-2 build
  step guards this.

---

# Definition of Done

- [ ] AC-1…AC-4 satisfied
- [ ] Ready for review
