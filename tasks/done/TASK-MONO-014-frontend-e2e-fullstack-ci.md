# TASK-MONO-014 — Frontend CI Phase 4: Full-stack Playwright E2E (web-store)

**Status**: done
**Completed**: 2026-04-27

## Goal

Wire the four existing full-stack Playwright specs (`golden-flow`,
`cart-management`, `auth-redirect`, `wishlist`) in
`apps/web-store/e2e/` into a new `frontend-e2e` CI job backed by a
docker compose stack. This closes the gap left intentionally open in
TASK-MONO-013 and validates that the full ecommerce request path works
end-to-end: web-store → gateway → 12 Spring Boot microservices →
PostgreSQL/Kafka/Redis/Elasticsearch/MinIO.

## Background

TASK-MONO-013 added a smoke-level Playwright job (`frontend-e2e-smoke`)
that requires no backend stack. The existing four full-stack specs in
`e2e/` have always assumed `docker compose up` with all backends running
but were never wired into CI. TASK-MONO-014 is the follow-up that closes
that gap.

Frontend CI now has three layers:

- TASK-MONO-009: `frontend-checks` (lint + prod build)
- TASK-MONO-010: `frontend-unit-tests` (vitest)
- TASK-MONO-013: `frontend-e2e-smoke` (Playwright, no backend)
- TASK-MONO-014 (this task): `frontend-e2e` (Playwright, full docker compose)

## Scope

**In scope:**

1. `playwright.config.ts` — add a `webServer` block active only in CI
   (`process.env.CI`). The webServer starts `pnpm start` on port 3000
   with `API_URL_INTERNAL` and `NEXT_PUBLIC_API_URL` pointed at the
   gateway on `http://localhost:18080` (PORT_PREFIX=1 default).
   Locally, `webServer` remains undefined; developers use
   `docker compose up` which includes the web-store container.

2. `ecommerce-boot-jars` job in `.github/workflows/ci.yml` — add an
   artifact upload step for all 12 Spring Boot JARs (`retention-days: 1`).
   The job comment is updated to reflect the new downstream consumer.
   `actions/upload-artifact@v4` strips the longest common prefix
   (`projects/ecommerce-microservices-platform/apps/`) so the artifact
   root contains `<service>/build/libs/<service>.jar`.

3. New `frontend-e2e` job in `.github/workflows/ci.yml`:
   - `if: github.repository == 'kanggle/monorepo-lab'` — scoped to the
     monorepo; extracted portfolio repos have no backend stack and are
     covered by the smoke job.
   - `needs: [ecommerce-boot-jars, frontend-checks]` — JAR artifacts
     + passing build/lint required before starting.
   - `timeout-minutes: 60` — docker compose build + health-check boot
     (360s `start_period` on backends) + test run.
   - Downloads the `ecommerce-boot-jars` artifact and restores the 12
     JARs to `apps/<service>/build/libs/<service>.jar` (the path each
     Dockerfile's `COPY apps/<service>/build/libs/*.jar app.jar` expects).
   - Generates a synthetic `.env` in the ecommerce project root with
     test-safe values (fake JWT secret, fake DB passwords, Toss
     placeholder keys). No real secrets required; tests stop before any
     actual payment or OAuth flow.
   - Builds web-store with `pnpm --filter web-store build` (Next.js
     prod build required before `pnpm start`).
   - `docker compose up --build -d` targeting only the functional
     services — observability stack (Jaeger, Prometheus, AlertManager,
     Grafana, Loki, Promtail) is omitted to stay within the runner's
     7 GB RAM. OTEL exporters in the backends log connection errors
     against the absent Jaeger endpoint; these are non-blocking.
   - Health-check loop polls `http://localhost:18080/actuator/health`
     every 10s for up to 7 minutes (matching the 360s `start_period` in
     docker-compose.yml backend healthchecks plus margin).
   - `pnpm --filter web-store exec playwright install --with-deps chromium`
   - `pnpm --filter web-store run e2e` — runs the four existing specs.
   - `docker compose down` on `always()` for cleanup.
   - Upload `playwright-report/` + `test-results/` on failure.

**Out of scope:**

- Observability stack in CI (excluded for RAM budget reasons).
- Running full-stack specs in extracted portfolio repos (smoke job covers them).
- Adding new specs — the four existing ones are wired as-is.
- Search-service E2E (Elasticsearch / Nori flakiness noted in golden-flow spec comment).
- OAuth login flow (tests use email/password signup/login via `signupAndLogin` helper).
- Actual Toss payment confirmation (tests stop at the checkout page).

## Acceptance Criteria

1. `frontend-e2e` job appears in ci.yml and runs only on
   `kanggle/monorepo-lab`.
2. `ecommerce-boot-jars` job uploads the `ecommerce-boot-jars` artifact
   (12 JARs) after a successful build.
3. `playwright.config.ts` exposes a `webServer` block in CI that starts
   web-store with the gateway URL, and `undefined` locally.
4. The four existing full-stack specs can be invoked end-to-end through
   the new CI job without modifying any spec file.
5. Existing jobs (`build-and-test`, `boot-jars`, `ecommerce-boot-jars`,
   `frontend-checks`, `frontend-unit-tests`, `frontend-e2e-smoke`,
   `integration-tests`, `e2e-tests`) are unchanged in behavior.

## Related Specs

None (CI plumbing only — no new spec files added).

## Related Contracts

None.

## Edge Cases

- **RAM budget**: The GitHub-hosted ubuntu-latest runner has ~7 GB RAM.
  With memory limits configured in docker-compose.yml (10 × 256 MB
  PostgreSQL + 12 × 512–768 MB Spring Boot + 512 MB Kafka + 1 GB
  Elasticsearch + 512 MB MinIO), *limits* exceed available RAM but actual
  RSS under light E2E load is much lower. Risk: if a service is OOM-killed,
  a health check will fail and the job will timeout after 7 min with a log
  dump from gateway-service. Mitigation: if this becomes a recurring
  problem, either reduce Spring Boot memory limits or use a large runner.
- **OTEL connection noise**: Backends have `OTEL_TRACES_EXPORTER=otlp`
  pointing at `http://jaeger:4318`. With Jaeger excluded, each backend
  will log OTEL export failures with exponential backoff. This adds log
  noise but does not affect HTTP request handling or test correctness.
- **Elasticsearch Nori build**: `infra/elasticsearch/Dockerfile` installs
  the Nori analyzer plugin — this requires a network call on every cold
  Docker build. If GitHub Actions blocks the plugin download, the
  Elasticsearch service will fail to start. Mitigation: check the build
  log; the Nori Dockerfile is in `infra/elasticsearch/Dockerfile`.
- **`next start` + `output: 'standalone'` warning**: Same as TASK-MONO-013
  edge case — `next start` emits a warning but the regular `.next/server`
  artifacts are present alongside the standalone bundle so tests pass.
- **PORT_PREFIX default**: docker-compose.yml defaults `PORT_PREFIX` to
  `1`, so all host ports are `1XXXX`. The gateway ends up on `18080`;
  `playwright.config.ts` and the health-check loop both use this port.
  If a future compose change alters the default prefix, update
  `GATEWAY_PORT` env var and the health-check URL.
- **Synthetic .env secrets**: JWT_SECRET and DB passwords are ephemeral
  test values generated in CI. They match only for the duration of that
  job's docker compose stack. No real data is at risk.

## Failure Scenarios

- **OOM on runner**: A backend service is killed mid-boot. Symptom:
  gateway health check times out; log dump shows a service whose
  container exited. Fix: reduce memory limits or switch to a large runner.
- **Elasticsearch start failure**: Nori plugin download blocked or
  Elasticsearch JVM OOM. Symptom: search-service fails its health check
  and gateway waits indefinitely (or times out). Note: search-service
  is only used for `/search` — the four specs do not hit `/search`, but
  gateway still waits for search-service to be healthy before considering
  itself healthy. Workaround if needed: remove search-service (and
  elasticsearch) from the docker compose up service list and update
  gateway's depends_on (requires a separate compose override).
- **Artifact size**: 12 fat JARs ≈ 1–1.5 GB total. GitHub Actions
  artifact upload/download has a 10 GB per workflow run limit; 1.5 GB is
  well within it. If future services bloat JARs, monitor artifact size.
- **`next start` hard error**: See TASK-MONO-013 failure scenarios.
  Workaround: gate `output: 'standalone'` behind `NEXT_DISABLE_STANDALONE`
  or switch to `node .next/standalone/apps/web-store/server.js`.
- **Rate limiting**: `signupAndLogin` helper adds a 300ms pause between
  signup and login to avoid gateway's brute-force rate limit window. If
  the CI runner is faster than expected and hits 429, increase the pause
  in the helper or add `retries: 2` in playwright.config.ts.

## Outcome (2026-04-27)

Three files changed:

- `apps/web-store/playwright.config.ts` — `webServer` block added for CI;
  `GATEWAY_URL` constant (defaults to `http://localhost:18080`).
- `.github/workflows/ci.yml` — `ecommerce-boot-jars` job updated (upload
  step added, comment revised); new `frontend-e2e` job.
- `tasks/done/TASK-MONO-014-frontend-e2e-fullstack-ci.md` — this file.

No spec files, no helper files, no turbo.json changes (full-stack E2E
is docker-state-dependent; wrapping in turbo cache would be incorrect).

AC 1–3 verified by code inspection. AC 4–5 verified once the PR's CI run
completes on `kanggle/monorepo-lab`.
