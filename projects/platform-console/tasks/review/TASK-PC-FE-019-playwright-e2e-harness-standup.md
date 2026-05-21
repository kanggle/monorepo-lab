# Task ID

TASK-PC-FE-019

# Title

platform-console Playwright e2e harness standup — full-stack docker-compose CI overlay (GAP admin/auth/account + finance svc + console-bff + console-web), seed SQL fixtures (SUPER_ADMIN caller + non-self target operator + finance account), Playwright login fixture, nightly CI integration; activates 2 deferred specs (PC-FE-016 operators-profile + PC-FE-017 operators-admin-profile) — closes TASK-PC-FE-016/017/018 honest gap (a) "Playwright e2e SKIPPED, harness not stood up"

# Status

review

# Owner

frontend

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- test
- deploy

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **depends on**: TASK-PC-FE-016 (me/profile UI, **DONE** 2026-05-21) + TASK-PC-FE-017 (admin/{id}/profile UI, **DONE** 2026-05-22) + TASK-PC-FE-018 (admin dialog current-value pre-population, **DONE** 2026-05-22). All vertical-slice production code + spec click sequences preserved in `tests/e2e/operators-profile.spec.ts` + `tests/e2e/operators-admin-profile.spec.ts` with `test.skip(true)`. The harness this task stands up will simply remove the `test.skip(true)` guards.
- **depends on**: TASK-BE-304 / BE-306 / BE-307 / BE-308 (full producer chain, all DONE). The seed SQL fixture inserts a row into `admin_operators` (V0029 column already on main) + a known finance account UUID (finance-platform side seed).
- **origin**: TASK-PC-FE-016 § Honest gaps (a) "Playwright e2e SKIPPED — tests/e2e/ harness not stood up; vertical-slice coverage 는 unit + IT + PC-FE-014 IT 가 커버; future harness task 가 light up" + identical text in TASK-PC-FE-017 § Honest gaps (a). Both tasks explicitly defer to this follow-up.
- **prerequisite for**: nothing (closes the harness gap; future PR-time smoke is a separate task — see § Out of Scope).
- **spec-first**: spec PR (this file + INDEX) → impl PR (CI overlay docker-compose + seed SQL + Playwright login fixture + 2 spec activation + workflow integration) → close chore PR.
- **no ADR** (HARDSTOP-09 not triggered): the harness is *infrastructure* — no architectural decision, no permission change, no contract change, no per-domain test policy change. Reuses the **established TASK-MONO-014 / TASK-MONO-024 frontend-e2e-fullstack pattern** (ecommerce nightly Playwright + docker-compose CI overlay) verbatim, adapted to platform-console's service set.

---

# Goal

PC-FE-016 / PC-FE-017 / PC-FE-018 의 vertical-slice 가 production code + unit + slice + IT 까지 완성된 상태에서 last gap = "browser-driven click sequence 검증 부재". 두 spec 파일 (`operators-profile.spec.ts`, `operators-admin-profile.spec.ts`) 가 명세 click sequence 보존 + `test.skip(true)` guard — harness 한 번 라이팅하면 단순 light-up.

Stand up the missing harness:

1. **Docker-compose CI overlay** (`projects/platform-console/docker-compose.e2e.yml` — new) — minimal 6-service stack within the GitHub Actions runner's 7 GB RAM budget (ecommerce-fullstack-pattern):
   - **GAP admin-service** (operator JWT issuer, `/api/admin/console/registry`, `/api/admin/me`, `/api/admin/operators*`, `/api/admin/operators/{operatorId}/profile` etc.)
   - **GAP auth-service** (OIDC AS — `platform-console-web` public client; PKCE login flow)
   - **GAP account-service** (account read; consumed by finance via gateway when needed for v1 finance balance — IF finance-platform needs account-service for its read path; check at impl)
   - **finance-platform** (single service — finance gateway or single combined image; whatever serves `/api/finance/balances/{accountId}` that console-bff `FinanceBalanceReadAdapter` consumes)
   - **console-bff** (Spring Boot — `GET /api/console/dashboards/overview` orchestrator)
   - **console-web** (Next.js — Playwright target)
   - **MySQL + Redis** (shared, single instance each — admin-service + auth-service + account-service + finance-platform schemas live in the same MySQL)
   - **No Kafka / no Traefik / no observability stack** (mirror ecommerce CI overlay — Traefik replaced by host-port publish; OTEL exporter errors non-blocking).
2. **Seed SQL fixtures** (`projects/platform-console/apps/console-web/tests/e2e/fixtures/seed.sql` — new) — applied via Flyway profile OR direct `mysql` exec:
   - 2 rows in `admin_operators`: (a) `e2e-super-admin` (caller) with SUPER_ADMIN role + platform-scope `tenant_id='*'`, plaintext password hashed via Argon2id; (b) `e2e-target-operator` (non-self target) with operator role + tenant `fan-platform`, NO `finance_default_account_id` (so PC-FE-016 me/profile + PC-FE-017 admin/{id}/profile + PC-FE-018 admin dialog 모두 검증 가능).
   - 1 finance account row in finance-platform with id `e2e-account-uuid-7` + non-zero balance (so PC-FE-016 의 vertical-slice "set → overview shows ok" assertion 이 검증됨).
3. **Playwright login fixture** (`tests/e2e/fixtures/login.ts` — new) — `loginAsSuperAdmin(page)` helper. Drives the full OIDC PKCE login flow against the running stack (`/login` → AS authorize → callback → token exchange). Caches the resulting authenticated state via Playwright `storageState` for test-isolation.
4. **2 spec activation** — remove `test.skip(true)` guard + integrate the login fixture + assert the click sequences end-to-end:
   - `operators-profile.spec.ts` (PC-FE-016) — operator self-sets finance default account → dashboards/overview finance card renders `ok` with balance data.
   - `operators-admin-profile.spec.ts` (PC-FE-017 + PC-FE-018 visibility) — SUPER_ADMIN sets target operator's finance default account → dialog opens with current-value pre-population on re-open (PC-FE-018) → producer 204 → audit row written.
5. **CI workflow integration** — add a new nightly job to `.github/workflows/nightly-e2e.yml` (sibling of `frontend-e2e-fullstack`) named `platform-console-e2e-fullstack` (or similar). Mirror the ecommerce job's structure: boot-jars artifact upload (admin-service + auth-service + account-service + finance gateway + console-bff Spring Boot apps + console-web `pnpm build`) → docker-compose.e2e.yml up → Playwright run → boot-jars artifact cleanup. **Nightly only** — PR-time smoke is a separate follow-up task (see § Out of Scope).

After this task, two production-grade e2e specs run nightly + observable click sequence regression. Lights up the 8-task chain's last documented gap.

# Decision authority

- **Why nightly-only (NOT PR-time)**:
  - 6-service docker-compose orchestration burn rate on the GitHub Actions runner (~3-5 min spin-up + ~2-3 min Playwright run = 5-10 min total per cycle) > PR-time budget of ~3-5 min for CI-RED-at-merge avoidance. Memory `project_e2e_3phase_strategy_complete.md` 가 ecommerce 의 3-phase (Phase 1 nightly + Phase 2 PR-time + Phase 3 acceleration) 를 reference 로 추천 — nightly 부터 lighting up, PR-time smoke 는 추후 phase.
  - The PR-time path is already covered by unit + slice + console-bff IT + admin-service IT (BE-306/307/308 = real DB + real OIDC token verify + real audit row writes). E2E adds *browser-driven click sequence verification* — high-value for regression but lower freshness urgency than producer-side IT.
- **Why ecommerce frontend-e2e-fullstack pattern (NOT a new infrastructure pattern)**:
  - Established + battle-tested in main (TASK-MONO-014 / TASK-MONO-024). Avoids inventing a new docker-compose overlay convention. The platform-console adaptation only swaps ecommerce-specific services for GAP+finance+console-bff+console-web.
  - Reuse of `docker-compose.e2e.yml` filename convention + nightly-e2e.yml structural sibling + boot-jars artifact upload from a `nightly-boot-jars` job.
- **Why minimal 6-service stack (NOT full GAP 11+ service docker-compose.yml)**:
  - Runner's 7 GB RAM budget. Ecommerce CI overlay deliberately excludes Jaeger/Prometheus/Grafana/Loki/Promtail; platform-console adopts the same exclusion + further trims to ONLY the services the 2 e2e specs actually exercise (admin + auth + account [if needed] + finance + bff + web).
- **Why no Kafka / no Traefik in CI overlay**:
  - Traefik replaced by host-port publish for each service (admin-service:8080, console-web:3000, etc.). Specs use `CONSOLE_BASE_URL=http://localhost:3000` (or wherever console-web publishes). The dev path remains `pnpm traefik:up + http://console.local/`.
  - Kafka excluded because the 2 e2e specs do NOT exercise an event-publishing code path; only `/api/admin/operators/...profile` mutations which write to `admin_actions` synchronously. (If a future spec needs event verification, Kafka can be added then.)
- **Why 2 spec only (NOT lighting up additional login/catalog/tenant-switch specs)**:
  - The harness exists to close the PC-FE-016/017/018 honest gap (a). Additional specs (login flow smoke, catalog navigation, tenant switch) are valuable but ORTHOGONAL — they belong in a separate "console-web smoke spec expansion" follow-up task. Lumping them here would inflate the impl PR scope past the CLAUDE.md guidance of one focused-scope task.
- **Why seed SQL via fixture file (NOT a dedicated Flyway migration)**:
  - The seed rows are *test fixtures* — they must NOT bleed into production migrations. A test-only `seed.sql` exec'd in CI between `docker-compose up` and Playwright run keeps prod migrations clean. The pattern mirrors ecommerce frontend-e2e-fullstack's environment-variable test placeholders + matches platform-console's no-DB Model B convention (the seed targets the federated GAP+finance DBs, not console's own).
- **Why no producer change**:
  - All 4 producers (BE-304/306/307/308) are prod-grade. The e2e specs consume the existing endpoints via the running stack — no new producer surface needed. AC-3 verifies 0 byte diff across `projects/global-account-platform/`.
- **Why no console-bff change**:
  - console-bff `GET /api/console/dashboards/overview` orchestrator is already prod-grade (PC-BE-001/002/003 + Phase 7 MVP). The harness exercises it as-is. AC-4 verifies 0 byte diff across `projects/platform-console/apps/console-bff/src/**` (ADR-MONO-017 D4 HARD INVARIANT preserved).

---

# Scope

## In Scope

**Specs (spec PR — this PR)**:

- This task file.
- `projects/platform-console/tasks/INDEX.md` — ready entry.

**Code (impl PR — out of scope here, listed for the dispatch agent to know the shape)**:

- `projects/platform-console/docker-compose.e2e.yml` (new) — minimal 6-service CI overlay (admin-service + auth-service + account-service + finance gateway + console-bff + console-web + shared MySQL + Redis). Host-port publishing (no Traefik). Health-checks per service. Mirrors ecommerce `docker-compose.ci.yml` overlay style.
- `projects/platform-console/apps/console-web/tests/e2e/fixtures/seed.sql` (new) — 2 admin_operators rows (caller + target) + 1 finance account.
- `projects/platform-console/apps/console-web/tests/e2e/fixtures/login.ts` (new) — `loginAsSuperAdmin(page)` Playwright fixture; persists `storageState` for reuse.
- `projects/platform-console/apps/console-web/tests/e2e/operators-profile.spec.ts` — remove `test.skip(true)`; integrate `loginAsSuperAdmin` (or login-as-operator); assert finance card `ok` after self-set.
- `projects/platform-console/apps/console-web/tests/e2e/operators-admin-profile.spec.ts` — remove `test.skip(true)`; integrate `loginAsSuperAdmin`; assert per-row dialog opens with current-value (PC-FE-018), Save → 204 → dialog closes; (optional) re-open + verify current-value reflects the new state.
- `projects/platform-console/apps/console-web/playwright.config.ts` — extend with `webServer` config (if it's the Playwright runner that starts console-web) OR keep as-is (if docker-compose handles); add `storageState` global setup if used.
- `.github/workflows/nightly-e2e.yml` — new job `platform-console-e2e-fullstack` (mirrors `frontend-e2e-fullstack` structure):
  - `platform-console-boot-jars-nightly` upstream job that builds + uploads admin-service, auth-service, account-service, finance gateway, console-bff boot jars.
  - The fullstack job downloads the artifacts, brings up docker-compose.e2e.yml, applies seed.sql, runs Playwright with `--project=chromium`, uploads playwright-report on failure.
  - Scoped to `kanggle/monorepo-lab` (same as ecommerce fullstack — extracted portfolio repos have no backend stack).
- (possibly) `projects/platform-console/apps/console-web/package.json` — `test:e2e` script wired to Playwright if not already.

**Tests** (impl PR):

- The 2 specs themselves are the test plan — no separate "harness verification" unit tests beyond CI green.

## Out of Scope

- **PR-time smoke harness** (ecommerce + fan-platform pattern: `playwright.smoke.config.ts` + closed-loopback URL + mock backends). Separate follow-up task (TASK-PC-FE-021 or similar). Reasoning: scope discipline + memory's 3-phase strategy.
- **Additional spec activation** (login/catalog/tenant switch / multi-tenant isolation / dashboards-overview composition): orthogonal to the 8-task chain honest gap closure. Each is a sibling spec for a future "spec expansion" task.
- **BE producer change**: all producers are prod-grade. AC-3 verifies 0 byte diff.
- **console-bff change**: prod-grade. AC-4 verifies 0 byte diff.
- **Other producer zero-retrofit**: AC-5 verifies 0 byte diff.
- **ADR amendment**: none. Pure infrastructure addition.
- **Local dev harness improvements** (pnpm script convenience etc.): out of scope. The CI overlay is the focus.
- **Observability stack in CI**: explicitly excluded (mirrors ecommerce CI overlay).
- **Spring Boot service Docker image build optimisations**: out of scope; reuse existing per-service Dockerfile.

# Acceptance Criteria

- **AC-1 (spec PR atomic)**: spec PR lands exactly **2 files** — this task file + INDEX. No code; no docker-compose; no workflow.
- **AC-2 (impl PR: docker-compose CI overlay)**: `projects/platform-console/docker-compose.e2e.yml` exists, with services exactly matching § Scope. All services have health-checks. No Traefik. Host-port publish per service.
- **AC-3 (no GAP producer change)**: 0 byte diff across `projects/global-account-platform/` in the impl PR.
- **AC-4 (no console-bff change)**: 0 byte diff across `projects/platform-console/apps/console-bff/src/**` in the impl PR. ADR-MONO-017 D4 HARD INVARIANT preserved.
- **AC-5 (zero-retrofit other producers)**: 0 byte diff across `projects/{wms,scm,erp,fan,ecommerce}-platform/`. `projects/finance-platform/` MAY have a non-byte-zero seed reference if the seed SQL touches finance schema (the test-only data goes through the docker-compose-mounted seed.sql exec, NOT a Flyway migration in finance-platform proper — so finance-platform code diff stays empty too). Verified at impl.
- **AC-6 (2 spec activation)**: `operators-profile.spec.ts` + `operators-admin-profile.spec.ts` both pass with `test.skip` removed.
- **AC-7 (login fixture)**: `loginAsSuperAdmin(page)` exists and persists `storageState` for reuse across test runs in a single Playwright run.
- **AC-8 (nightly CI integration)**: `.github/workflows/nightly-e2e.yml` includes the new `platform-console-e2e-fullstack` job. Runs on nightly schedule. Uploads playwright-report on failure. Scoped to `kanggle/monorepo-lab`.
- **AC-9 (parity matrix unchanged)**: `parity-verification.test.ts` expected count remains **18**.
- **AC-10 (BE-303 3-dim verified at close chore)**: per CLAUDE.md, close chore opens only after impl PR satisfies all three dims (state=MERGED + mergeCommit match + 0 failing pre-merge).
- **AC-11 (BE-299 done re-stage check at close chore)**: per CLAUDE.md, `git mv ready/ → done/` + Status flip + `git add` + `git show :<done-path>` confirms `Status: done`.

# Related Specs

- `projects/global-account-platform/specs/contracts/http/admin-api.md` — **byte-unchanged**. Producer endpoints consumed via running stack.
- `projects/platform-console/specs/contracts/console-integration-contract.md` — **byte-unchanged**. Harness is infrastructure, not a contract change.
- `docs/adr/ADR-MONO-017-platform-console-bff-architecture.md` — **byte-unchanged**.
- `docs/adr/ADR-MONO-010 / ADR-MONO-011` — **byte-unchanged**. (ecommerce e2e 3-phase strategy ADRs; this task adopts the established pattern without amendment.)

# Related Contracts

- `projects/global-account-platform/specs/contracts/http/admin-api.md` — producer contract consumed (already on main).
- finance-platform balance read contract — consumed (already on main).

# Edge Cases

- **MySQL container startup timing**: admin-service, auth-service, account-service, finance all depend on MySQL. Health-checks + `depends_on: condition: service_healthy` orchestration; mirrors ecommerce CI overlay.
- **Auth-service OIDC token issuance timing**: the Playwright login fixture must wait for the AS to be fully up. Implemented via a `waitForResponse(/jwks/)` or polling the `/.well-known/openid-configuration` endpoint before driving the login flow.
- **Cross-tenant operator targeting in PC-FE-017**: the seed inserts the target in tenant `fan-platform`; the caller is platform-scope (`*`). This is the tested cross-tenant scenario (per BE-307 IT case 1).
- **Finance account UUID not seeded → finance balance read 404**: the PC-FE-016 spec's "overview shows ok" assertion requires a valid finance account row. Seed includes 1 row; the spec uses the same UUID.
- **Argon2id password hashing in seed**: the seed SQL must include a real Argon2id hash of a known password (e.g. `E2eTestPass1!`). Generated once at seed-authoring time; checked into the fixture.
- **CI runner memory pressure**: 6 services + MySQL + Redis on a 7 GB runner. If services OOM, drop account-service (verify whether the 2 specs strictly need it; PC-FE-016 finance balance read may not require it if finance has its own account model).
- **Playwright trace upload size**: ecommerce limits trace to on-first-retry; this task adopts the same setting (`playwright.config.ts` already does so).
- **Flyway migration drift between seed time and CI**: seed.sql must NOT contain DDL — only INSERTs against the schema that the docker-compose'd services own (their Flyway migrations run on `up`). Pure data seed.

# Failure Scenarios

- **The 2 specs flake due to timing/orchestration**: mitigated by `condition: service_healthy` + explicit `waitForResponse` in the login fixture. CI `retries: 2` (already set in playwright.config.ts) provides redundancy.
- **Docker-compose CI overlay exceeds runner RAM**: profile down to absolute minimum — admin + auth + finance + console-bff + console-web + MySQL + Redis (6 services). Account-service may be droppable.
- **Producer change required mid-impl** (unlikely — all 4 producers are prod-grade): STOP and HARDSTOP-09 (architecture decision); add a sibling task; do NOT silently change a producer.
- **`Idempotency-Key` header drift surfaces in e2e** (PC-FE-017 AC-7 defense at unit-level): the e2e specs do NOT need to re-assert header matrix invariants — those are unit-tested. E2e is browser-driven, not header-driven.
- **The harness leaks fixtures across spec runs**: enforce `test.beforeEach` resets via the seed.sql replay OR each spec uses its own UUIDs (`e2e-test-${uuid}`) per BE-306 cycle 2 hermetic pattern.
- **A reviewer suggests adding more specs in the same PR**: reject. The 2-spec scope is by design (§ Decision authority "Why 2 spec only"). Additional specs are a follow-up.

# Verification

1. Spec PR diff: `git diff --stat origin/main` shows exactly **2** changed files — this task file + INDEX.
2. Impl PR (separate, after spec PR merges):
   - Local: `pnpm --filter console-web exec playwright test` GREEN against a manually-up'd docker-compose.e2e.yml stack (Windows host: WSL2 / Linux VM; CI Linux runner is authoritative).
   - CI nightly: the new `platform-console-e2e-fullstack` job GREEN on the nightly schedule's next run.
   - AC-3 / AC-4 / AC-5 grep zero diff.
   - AC-9 `parity-verification.test.ts` count = 18.
3. Close chore (after impl GREEN): BE-303 3-dim + BE-299 re-stage check.

분석=Opus 4.7 / 구현 권장=Opus 4.7 (cross-stratum: docker-compose orchestration + seed SQL + Playwright fixture + 2 spec activation + CI workflow — multiple integration seams; the harness orchestration is net-new judgement; deserves Opus) / 리뷰=Opus 4.7 (dispatcher 독립 재검증 — AC-1 file count / AC-2 docker-compose service set / AC-3+4+5 byte-diff grep / AC-6 both specs GREEN / AC-7 login fixture / AC-8 nightly CI job / AC-9 parity count / AC-10+11 BE-303 + BE-299).
