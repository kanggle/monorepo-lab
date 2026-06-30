# Task ID

TASK-MONO-318

# Title

Activate the web-store GAP RP-initiated-logout E2E in nightly CI (add the `web-store-iam-logout-e2e` job from the TASK-INT-023 handoff)

# Status

done

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

- [x] **AC-1** — `web-store-iam-logout-e2e` job added to `nightly-e2e.yml`; additive
  (does not modify `frontend-e2e-fullstack`). (#2043)
- [x] **AC-2** — Job boots the lean GAP stack (`docker-compose.iam-e2e.yml`), waits for
  `auth-service` healthy, seeds the CONSUMER credential, builds web-store, and runs
  `rp-initiated-logout.spec.ts` with `SKIP_GAP_E2E=0`.
- [x] **AC-3** — Repo-scoped (`if: github.repository == 'kanggle/monorepo-lab'`) and
  YAML-valid.
- [x] **AC-4** — `workflow_dispatch` run `28431315032` GREEN — the logout job passed
  end-to-end (`✓ 1 passed (20.4s)`); build-step env wiring confirmed on the Linux runner.

> **Verification note**: this is a **nightly CI job that cannot be run on a local
> Windows host** (needs a Linux runner + the GAP docker stack; the web-store prod
> build needs symlink perms unavailable on Windows — local verification used
> `next dev`). The spec itself was verified locally against a real GAP
> (`1 passed (51.0s)`, per the handoff). AC-4 (the runner-side build-env wiring) is
> confirmed on the first nightly / `workflow_dispatch`, not locally. The job is
> additive + non-blocking, so a first-run env tweak does not affect main or PRs.

---

# Status update (2026-06-30) — back in `ready`: job NOT yet end-to-end green

The job was added (#2043) and one runner-side bug fixed (#2044), but it is **not yet
green end-to-end** — it is being un-blocked one CI step at a time across nightly runs.
Moved back to `ready/` because finishing it is actionable work (not awaiting a merge).

1. **Step "Build auth-service bootJar" — FIXED (#2044).** The handoff YAML ran
   `./gradlew :apps:auth-service:bootJar` with `working-directory: projects/iam-platform`,
   but the monorepo has a single ROOT gradlew. Corrected to run from `${{ github.workspace }}`
   with `:projects:iam-platform:apps:auth-service:bootJar` (verified locally — `auth-service.jar`
   builds). The job now passes this step.
2. **Step "Boot lean GAP stack" — NEXT BLOCKER (red as of nightly run 28427113105).**
   `docker compose -f docker-compose.iam-e2e.yml up -d --build` fails. Root cause not yet
   captured: local repro was blocked (the dev host's Docker daemon pipe was down at the time),
   and `gh run view --log` did not return the step output.

**Resume from here**: with **stable local Docker** (local Testcontainers works post-1.21.3),
reproduce by `cd projects/ecommerce-microservices-platform && docker compose -f
docker-compose.iam-e2e.yml up -d --build` to capture the compose failure (likely a build
context / jar-path / image issue), **or** download the nightly run logs from the GitHub UI.
Then the remaining steps (seed, `pnpm --filter web-store build`, playwright) are still
unverified. AC-1/AC-3 met; AC-2/AC-4 pending. The job stays additive + non-blocking
(no impact on main/PRs) while red.

---

# Status update (2026-06-30 #2) — GREEN end-to-end (AC-2/AC-4 met)

Three runner-side root causes were diagnosed and fixed; `workflow_dispatch` run
`28431315032` is GREEN (`✓ 1 passed (20.4s)`):

1. **"Boot lean GAP stack" compose context** — `docker-compose.iam-e2e.yml` referenced the
   iam project as `../iam`, but the directory is `iam-platform`. Fixed all three references
   (mysql init volume, auth-service build context, JWT keys volume) → `../iam-platform`.
2. **Missing shared base image** — the auth-service image is `FROM monorepo/java-service-base:v1`
   (not on any registry). Added the "Build shared java-service-base image" step before
   `compose up --build` (same pattern as the other compose-based nightly jobs, ADR-MONO-041 D2).
3. **Credential login rejected as "Invalid email or password."** — TASK-BE-407 made GAP's
   form-login resolve the authoritative `tenant_type` from account-service
   (`CredentialAuthenticationProvider.tenantTypePort.resolve()`, a NON-fail-soft call AFTER
   the password verifies). The lean stack omitted account-service on a now-stale assumption,
   so the resolve failed closed and `login.html`'s `th:switch` default case rendered the
   generic credential error. The password itself verified fine; the roles claim (web-store's
   role-guard surface) is fail-soft and falls back to the local `RoleSeedPolicy` seed
   (`ecommerce → CUSTOMER`), so it never needed account-service. Fix: a tiny static nginx
   stub (`account-mock`, `apps/web-store/e2e/fixtures/account-mock.nginx.conf`) that answers
   the one non-fail-soft call `GET /internal/tenants/{tid} → {"tenantType":"B2C"}` and 404s
   the rest; auth-service pointed at it via `ACCOUNT_SERVICE_URL`.

`next start` (the Playwright `webServer`, run after `pnpm --filter web-store build`) emits a
harmless "does not work with output: standalone" warning but serves the regular `.next`
build fine — confirmed by the runner serving the login page; no webServer change needed.

All four ACs met. Status → `review` pending the impl PR merge + the standard 3-dimension
merge verification, then `review/ → done/`.

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

- [x] AC-1…AC-4 satisfied
- [x] Ready for review
