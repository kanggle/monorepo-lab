# TASK-MONO-013 — Frontend CI Phase 3: Playwright smoke E2E (web-store)

**Status**: done
**Completed**: 2026-04-27

## Goal

Add a `frontend-e2e-smoke` job to `.github/workflows/ci.yml` that runs
Playwright against the `web-store` Next.js prod build with no backend
stack required. Smoke-level only — three deterministic specs covering
home page render, login form, and the client-side auth guard redirect.

Full-stack Playwright (docker compose with all 12 ecommerce backends +
infra) is intentionally deferred to a follow-up monorepo task.

## Background

Frontend CI has progressed in stages:

- TASK-MONO-009: `frontend-checks` (lint + prod build).
- TASK-MONO-010: `frontend-unit-tests` (vitest).
- TASK-MONO-013 (this task): smoke-level Playwright.

Playwright is already a `devDependency` of `web-store` and there are
four full-stack specs under `apps/web-store/e2e/` (`golden-flow`,
`cart-management`, `auth-redirect`, `wishlist`). All four assume
`docker compose up` with the backend stack reachable on
`http://localhost:8080`. Wiring those into CI requires booting 12
Spring Boot services + Postgres + Redis + Kafka + Elasticsearch and
seeding test data — an order of magnitude more infrastructure than the
existing wms `e2e-tests` job (one gateway + one master, Testcontainers).

That full-stack profile is the right long-term shape but out of scope
here. The immediate gap CI does not catch is **Next.js prod build
hydration / route guard regressions** — production-only failure modes
that vitest + lint + build can miss (e.g. `useRequireAuth` redirecting
incorrectly, hydration mismatches breaking client islands). Smoke E2E
covers exactly that, runs in minutes on a single GitHub-hosted runner,
and works in extracted portfolio repos that have no backend stack.

## Scope

**In scope:**

1. New `apps/web-store/e2e-smoke/smoke.spec.ts` with three specs:
   - Home page (`/`) renders with HTTP 200 and the "인기 상품" section.
   - `/login` renders with email/password form fields.
   - Anonymous `/cart` access redirects to `/login` via the
     client-side `useRequireAuth` guard.
2. New `apps/web-store/playwright.smoke.config.ts`:
   - `testDir: './e2e-smoke'` (separate from full-stack `./e2e`)
   - `webServer` runs `pnpm start` on port 3000 with environment
     variables that point all API base URLs to a closed loopback
     (`http://127.0.0.1:1`). SSR fetches fail fast with ECONNREFUSED;
     each page exercises its `.catch(() => [])` / client-side guard
     fallback path. No backend required.
   - `outputDir: 'test-results-smoke'`,
     `reporter html outputFolder: 'playwright-report-smoke'` to keep
     artifacts disjoint from the full-stack profile.
3. `apps/web-store/package.json`: add `e2e:smoke` script.
4. `turbo.json`: add `e2e:smoke` task with `dependsOn: ["build"]`
   (transitively pulls `^build` for workspace deps).
5. Root `package.json`: add `e2e:smoke` script (`turbo e2e:smoke`),
   matching the existing `build` / `lint` / `test` pattern.
6. `.gitignore`: add `apps/*/test-results-smoke/` and
   `apps/*/playwright-report-smoke/`.
7. New `frontend-e2e-smoke` job in `.github/workflows/ci.yml`:
   - Same Node 20 / pnpm 9.15.0 / cache pattern as
     `frontend-checks` and `frontend-unit-tests`.
   - `pnpm --filter web-store exec playwright install --with-deps chromium`
     before the test step.
   - `pnpm e2e:smoke` to run the suite.
   - Upload `playwright-report-smoke/` + `test-results-smoke/` on
     failure.

**Out of scope:**

- Booting the ecommerce backend stack in CI (deferred to a future
  TASK-MONO).
- Running the existing four full-stack specs in CI.
- Adding Playwright to `admin-dashboard` (no specs there yet).
- Cross-browser projects (chromium only — adequate for smoke; firefox /
  webkit can be added when the full-stack profile arrives).

## Acceptance Criteria

1. `pnpm e2e:smoke` from `projects/ecommerce-microservices-platform`
   runs the three smoke specs and exits 0 with **no backend stack
   running**.
2. The smoke profile does not invoke or interfere with the existing
   `pnpm e2e` / `e2e:ui` scripts (those still target
   `playwright.config.ts` and require the full backend).
3. `.github/workflows/ci.yml` contains a `frontend-e2e-smoke` job that
   runs to completion within 15 minutes on a fresh runner.
4. Existing jobs (`build-and-test`, `boot-jars`, `ecommerce-boot-jars`,
   `frontend-checks`, `frontend-unit-tests`, `integration-tests`,
   `e2e-tests`) are unchanged in behavior.
5. The smoke job runs unconditionally (no `if: github.repository == ...`
   gate) — it works in extracted portfolio repos as well as
   monorepo-lab, since no backend is required.

## Related Specs

None (CI plumbing + new smoke harness only).

## Related Contracts

None.

## Edge Cases

- **`output: 'standalone'` warning**: `next.config.ts` declares
  `output: 'standalone'`, and `next start` emits
  `"next start" does not work with "output: standalone" configuration`.
  In practice the regular `.next/server` artifacts are still produced
  alongside the standalone bundle and `next start` serves them — the
  three smoke specs pass locally despite the warning. If a future Next
  version makes this warning a hard error, the fix is either (a) gate
  `output: 'standalone'` behind a `NEXT_DISABLE_STANDALONE` env var
  set in the smoke job, or (b) launch `node .next/standalone/apps/
  web-store/server.js` directly with a static-asset copy step. Document
  whichever lands.
- **SSR fetch latency**: pointing `API_URL_INTERNAL` at `127.0.0.1:1`
  yields ECONNREFUSED instantly (vs a connect-timeout that would
  inflate test runtime). Verified locally: full smoke suite ≈ 47s
  including build + webServer boot.
- **Dev vs prod parity**: the smoke profile uses the production build,
  not `next dev`. Hydration mismatches and bundling regressions only
  surface in prod, so dev-server smoke would miss the very class of
  regression this job is meant to catch.

## Failure Scenarios

- **CI runner cannot install chromium**: the `playwright install --with-deps`
  step needs apt-mediated system libraries (libnss3, libxkbcommon, ...).
  GitHub-hosted ubuntu-latest has these pre-warmed; if a future image
  drops a dep, surface the failure in the Install step rather than
  papering over with `|| true`.
- **`next start` rejects standalone build**: see Edge Cases above.
  Switch to env-gated standalone or direct standalone server launch.
- **Spec flake on first cold start**: webServer timeout is 60s; first
  prod page compile + hydration runs ≈ 9s locally. If the runner is
  slow, raise the per-test timeout or set `retries: 1` (already enabled
  under `process.env.CI`).
- **Backend reachable by accident**: if a future change wires SSR
  fetches through a hardcoded URL that bypasses
  `API_URL_INTERNAL` / `NEXT_PUBLIC_API_URL`, the smoke spec might
  silently start hitting a real service. Mitigation: keep the smoke
  config's loopback env vars covering every public API URL key the app
  reads. Add new keys here whenever the runtime config surface grows.

## Outcome (2026-04-27)

Six files changed, one new directory:

- `apps/web-store/e2e-smoke/smoke.spec.ts` — three smoke specs.
- `apps/web-store/playwright.smoke.config.ts` — separate testDir +
  webServer + ECONNREFUSED env.
- `apps/web-store/package.json` — `e2e:smoke` script.
- `turbo.json` — `e2e:smoke` task with `dependsOn: ["build"]`.
- `package.json` (project root) — `e2e:smoke` → `turbo e2e:smoke`.
- `.gitignore` — `playwright-report-smoke/` + `test-results-smoke/`
  patterns.
- `.github/workflows/ci.yml` — `frontend-e2e-smoke` job.

Local verification (`pnpm --filter web-store run e2e:smoke`):
3 passed (47.3s).

Acceptance criteria 1 and 2 met locally. AC 3 / 4 / 5 verified once the
PR's CI run completes.
