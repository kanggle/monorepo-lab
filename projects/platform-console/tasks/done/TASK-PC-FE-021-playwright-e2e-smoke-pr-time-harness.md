# Task ID

TASK-PC-FE-021

# Title

platform-console Playwright e2e PR-time smoke harness — backend-less `playwright.smoke.config.ts` (closed-loopback `127.0.0.1:1`) + `apps/console-web/e2e-smoke/` 3-spec suite (root redirect / `(console)` guard / `/login` render) + `package.json` `e2e:smoke` script + `frontend-e2e-smoke` CI job extension. PR-time fast-feedback complement to TASK-PC-FE-019's nightly full-stack harness (ADR-MONO-010/011 e2e 3-phase strategy Phase 2 applied to platform-console; mirrors ecommerce web-store + fan-platform-web smoke-pattern verbatim).

# Status

done

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

- **depends on**: TASK-PC-FE-019 (Playwright e2e harness standup — nightly full-stack), **DONE** 2026-05-22. The nightly harness ships the `playwright.config.ts` baseline + the 2 deferred spec activations (operators-profile + operators-admin-profile) which run against the real GAP+finance+console docker-compose stack. This task adds a *complementary* PR-time path that boots ONLY the console-web Next.js prod build with all backends forced to a closed-loopback host — fast (≈ 30 s budget; no docker compose, no boot jars) and deterministic (every SSR fetch / OIDC discovery fails immediately so `(console)` guard / `/login` fallback are exercised).
- **depends on**: TASK-PC-FE-016 (me/profile UI), TASK-PC-FE-017 (admin/{id}/profile UI), TASK-PC-FE-018 (admin dialog current-value pre-population) — all DONE. These shipped the page-level guards / session helpers / login surface that the smoke specs assert. No code from any of these tasks is modified.
- **origin**: ADR-MONO-010 (e2e taxonomy `@Tag("smoke")` / `@Tag("full")` + Gradle task family + PR-time CI smoke) + ADR-MONO-011 (e2e 3-phase nightly + push-to-main cadence) + `project_e2e_3phase_strategy_complete.md` memory § "잔존 후보 (Phase 2 PR-time smoke for platform-console)". Ecommerce web-store smoke-pattern (`projects/ecommerce-microservices-platform/apps/web-store/playwright.smoke.config.ts` + `e2e-smoke/smoke.spec.ts`) + fan-platform-web smoke-pattern (`projects/fan-platform/web/fan-platform-web/playwright.smoke.config.ts` + `e2e-smoke/{auth-guard,home,login}.spec.ts`) are reused verbatim in shape — platform-console is the 3rd app to adopt the convention.
- **prerequisite for**: nothing (closes the "PR-time smoke" gap surfaced by TASK-PC-FE-019 § Out of Scope; further smoke-spec additions belong in separate per-feature follow-ups).
- **spec-first**: spec PR (this file + INDEX) → impl PR (smoke config + 3 specs + package.json script + ci.yml job extension) → close chore PR.
- **no ADR** (HARDSTOP-09 not triggered): pure infrastructure addition under the already-ACCEPTED ADR-MONO-010 / ADR-MONO-011 / TASK-MONO-013 (frontend CI Phase 3) umbrella. No architectural decision, no permission change, no contract change.

---

# Goal

TASK-PC-FE-019 가 stood up nightly full-stack harness (~5-10 min/cycle, docker-compose 6 services). 이는 hard regression guard + production-parity 검증으로는 valuable 하지만 PR-time fast feedback 에는 부적합:

- **Author signal latency**: nightly cron (UTC 18:00 = KST 03:00) → 첫 surface 까지 ~3-23 h.
- **CI minute cost**: ~30-60 분 per nightly invocation (boot-jars + docker-compose orchestration + Playwright + cleanup).
- **PR-time non-coverage**: console-web 변경이 nightly 까지 click-sequence regression 시각화 안 됨.

The PR-time smoke pattern (ecommerce + fan-platform 의 mature precedent — `frontend-e2e-smoke` job in `.github/workflows/ci.yml`) fills this gap:

- **Closed-loopback URL** (`127.0.0.1:1`) 으로 모든 backend / OIDC discovery 호출 즉시 ECONNREFUSED → SSR fallback 경로 / middleware redirect / page 렌더 결정론적으로 활성화.
- **No backend** — `pnpm start` (Next.js prod build) 만 띄움. ~30 s budget per spec, ~60-90 s 전체 smoke run.
- **PR-time integration** — 기존 `frontend-e2e-smoke` job 에 platform-console-web 한 단계 추가 (ecommerce / fan-platform 와 같은 job 에서 순차 실행), paths-filter 가 console-web 변경 시 트리거.

이 task 가 standup 하는 surface:

1. **`projects/platform-console/apps/console-web/playwright.smoke.config.ts`** (new) — `testDir: './e2e-smoke'`, `webServer.command: 'pnpm start'`, `webServer.env` 가 OIDC_ISSUER_URL / CONSOLE_REGISTRY_URL / CONSOLE_TOKEN_EXCHANGE_URL / CONSOLE_BFF_URL 모두 `http://127.0.0.1:1` 으로 강제. Mirrors ecommerce / fan-platform smoke config 의 `closed-loopback` 패턴.

2. **`projects/platform-console/apps/console-web/e2e-smoke/`** (new directory) — 3 spec:
   - **`root-redirect.spec.ts`** — `GET /` 미인증 접근 → `/dashboards` 으로 server-side redirect → `(console)/layout.tsx` 의 `isAuthenticated()` 가드 발화 → 최종 URL `/login` + "Platform Console" 헤더 visible + "Global Account 로 로그인" CTA visible.
   - **`console-guard.spec.ts`** — 보호 경로 (`/operators`, `/dashboards/overview`) 미인증 접근 시 `(console)/layout.tsx` 가 `/login` 으로 redirect. `redirect` query param 보존은 확인 안 함 (`(console)/layout` 가 현재 query 미보존 — implementation 확인 후 spec 조정).
   - **`login-page.spec.ts`** — `GET /login?error=provider_error` → 한국어 에러 메시지 (`'GAP 로그인 중 오류가 발생했습니다. 다시 시도해주세요.'`) + GAP 로그인 트리거 CTA visible. 추가 error code (`invalid_state` / `state_mismatch` / `token_exchange_failed`) 각각 mapping 검증.

3. **`projects/platform-console/apps/console-web/package.json`** — `"e2e:smoke": "playwright test --config=playwright.smoke.config.ts"` script 추가 (`e2e` 는 nightly full-stack용으로 보존).

4. **`.github/workflows/ci.yml`** — 기존 `frontend-e2e-smoke` job 에 platform-console 한 단계 추가 (ecommerce / fan-platform 와 같은 job 에서 순차 실행). `if:` 조건에 `needs.changes.outputs.platform-console == 'true'` OR `needs.changes.outputs.workflows == 'true'` 추가. paths-filter `changes` 의 `platform-console` 패턴 확장 — 현재 `console-bff/**` + `docker-compose.yml` 만 cover; `console-web/**` 추가해야 platform-console FE 변경이 smoke job 을 트리거.

5. **`apps/console-web/.gitignore`** — `playwright-report-smoke/` + `test-results-smoke/` 추가 (TASK-PC-FE-019 의 nightly 용 `playwright-report/` + `test-results/` 형제).

스크립트 + spec 작성 후, 로컬 `pnpm --filter console-web e2e:smoke` GREEN + CI `frontend-e2e-smoke` job GREEN 확인.

# Decision authority

- **Why pure-positive paths-filter pattern over negation (MONO-074/075 quirk rule)**:
  - 메모리 `project_ci_path_filter_074_075_quirk.md` 의 hard rule: `dorny/paths-filter@v3` 의 `predicate-quantifier: 'some'` negation 이 file 을 잘못 "in" 분류함. console-web/** 패턴 추가는 positive ("이 경로 변경 시 트리거") 으로만 표현해야 함. console-bff/** 와 함께 OR 로 결합 (현재 platform-console 필터의 `console-bff/**` + `docker-compose.yml` 와 같은 형태).
  - 변경: `'projects/platform-console/apps/console-bff/**'` → 그대로 + 추가 `'projects/platform-console/apps/console-web/**'`.

- **Why extend existing `frontend-e2e-smoke` job (NOT a new job)**:
  - ecommerce + fan-platform 가 이미 같은 job 에서 순차 실행 — Node 20 + pnpm setup + Playwright install 셋업 비용을 한 번만 지불. console-web 추가도 같은 형태로 incrementally append.
  - 새 job 신설 = 별 GitHub Actions runner + setup 중복 비용 (~30 s × 2 = 1 min) + dependency duplication.
  - timeout-minutes 는 20 분 (현재) 이 console-web smoke (~60 s) 추가에도 충분.

- **Why closed-loopback URL `127.0.0.1:1` (NOT mock server)**:
  - Established pattern from ecommerce + fan-platform smoke. ECONNREFUSED 가 빠르고 (NXDOMAIN 대신 immediate TCP fail) deterministic. Mock server (msw / WireMock) 는 setup overhead + 새 dependency 가 smoke scope 와 어긋남.
  - SSR fetch 가 try/catch + fallback 경로를 가지고 있어야 (`(console)/layout.tsx` 가 이미 그렇게 — registry 실패 시 `tenants = []`).

- **Why 3 spec only (NOT a broader smoke suite)**:
  - 3 spec = (a) root redirect (b) `(console)` guard (c) `/login` render. console-web 의 *public path coverage* 가 본질적으로 이 3 개로 충분 (인증 후 페이지는 모두 backend 의존 → nightly full-stack 의 영역).
  - 추가 smoke 후보 (e.g. service catalog `/console` 의 fallback render, tenant switcher의 zero-tenant degraded UX) 는 후속 spec activation task 으로 별도 처리. 본 task 의 scope discipline 으로 *infrastructure standup* 자체에 집중.

- **Why no producer change / no console-bff change**:
  - smoke 가 closed-loopback 으로 backend 미기동 — producer side 호출 전혀 없음. AC-3 / AC-4 verify 0 byte diff across `projects/global-account-platform/` + `projects/platform-console/apps/console-bff/`.

- **Why `pnpm start` (NOT `pnpm dev`)**:
  - Production build 가 smoke 의 hard regression guard 의 본질 — middleware 의 `force-dynamic` 분기 + standalone server 의 `server.js` 부팅 경로 + RSC streaming 모두 `next start` 가 cover. `next dev` 는 HMR 등 dev-only 분기가 활성화돼 smoke 의 production-parity 검증 무력화.
  - 단 prod build 가 spec 실행 전 1 회 필요 (`pnpm --filter console-web build`) — ci.yml step 으로 추가 (ecommerce / fan-platform 와 같은 패턴).

- **Why `redirect` query 보존 검증 제외**:
  - 현재 `(console)/layout.tsx` 의 `redirect('/login')` 가 query 미보존 (NextAuth-style `from=<original>` 없음). 보존 도입은 별 follow-up (UX 개선). smoke 가 *현재 동작* 을 보호하는 것 — 향후 query 보존 추가 시 smoke spec 도 같이 업데이트.

# Scope

## In Scope

**Specs (spec PR — this PR)**:

- This task file.
- `projects/platform-console/tasks/INDEX.md` — ready entry.

**Code (impl PR — out of scope here, listed for the dispatch agent to know the shape)**:

- `projects/platform-console/apps/console-web/playwright.smoke.config.ts` (new) — closed-loopback `webServer.env`, `testDir: './e2e-smoke'`, `retries: process.env.CI ? 1 : 0`, `workers: 1`, locale `ko-KR`.
- `projects/platform-console/apps/console-web/e2e-smoke/` (new directory) — 3 spec files (root-redirect / console-guard / login-page).
- `projects/platform-console/apps/console-web/package.json` — `"e2e:smoke"` script 추가.
- `projects/platform-console/apps/console-web/.gitignore` — `playwright-report-smoke/` + `test-results-smoke/` 추가.
- `.github/workflows/ci.yml` — `frontend-e2e-smoke` job 의 `if:` 조건에 `platform-console` 트리거 추가 + 3 step 추가 (install console-web deps + build console-web + run smoke). `changes` filter `platform-console` 패턴에 `console-web/**` 추가. (참고: ecommerce / fan-platform 와 동일 패턴.)

**Tests** (impl PR):

- 3 smoke spec 자체가 test plan — Vitest unit suite 와 nightly full-stack 은 byte-unchanged.

## Out of Scope

- **Authenticated-flow smoke** (인증 후 페이지 렌더 / API 호출): smoke 의 closed-loopback constraint 와 incompatible — nightly full-stack 의 영역.
- **추가 smoke 후보** (서비스 카탈로그 `/console` fallback / tenant switcher zero-tenant degraded 등): 별 follow-up spec.
- **nightly job 의 trigger 변경** (PC-FE-019 의 `platform-console-e2e-fullstack` 은 nightly cron + push-to-main 만 — unchanged).
- **BE producer change**: 0 byte diff verified.
- **console-bff change**: 0 byte diff verified.
- **ADR amendment**: ADR-MONO-010/011 의 직접 적용 — 새 ADR 불요.
- **Vitest unit / integration suite 변경**: smoke 와 orthogonal.

# Acceptance Criteria

- **AC-1 (spec PR atomic)**: spec PR 가 정확히 **2 파일** 변경 — 본 task file + INDEX. 코드 / config / workflow 일체 변경 없음.
- **AC-2 (impl PR: smoke config)**: `projects/platform-console/apps/console-web/playwright.smoke.config.ts` 존재 + ecommerce / fan-platform 패턴과 일치 (closed-loopback env, testDir, `pnpm start` webServer, 1 worker, chromium project).
- **AC-3 (no GAP source change)**: `git diff --stat origin/main -- projects/global-account-platform/` = empty.
- **AC-4 (no console-bff source change)**: `git diff --stat origin/main -- projects/platform-console/apps/console-bff/src/` = empty.
- **AC-5 (zero-retrofit other producers)**: `git diff --stat origin/main -- 'projects/{wms,scm,erp,fan,ecommerce,finance}-platform/'` = empty.
- **AC-6 (3 smoke spec)**: `apps/console-web/e2e-smoke/` 가 정확히 3 spec — root-redirect / console-guard / login-page.
- **AC-7 (`e2e:smoke` script)**: `package.json` 에 `"e2e:smoke": "playwright test --config=playwright.smoke.config.ts"` 추가. 기존 `"e2e": "playwright test"` (full-stack nightly용) 는 unchanged.
- **AC-8 (CI integration)**: `.github/workflows/ci.yml` `frontend-e2e-smoke` job 에 `platform-console-web` build + smoke run 단계 추가, `if:` 조건에 `needs.changes.outputs.platform-console == 'true'` 추가, `changes` filter `platform-console` 패턴에 `apps/console-web/**` 추가. 모든 path 패턴은 pure-positive (negation 금지 — MONO-074/075 rule).
- **AC-9 (parity matrix unchanged)**: `parity-verification.test.ts` count = **18** (smoke 가 contract operation 추가 아님).
- **AC-10 (local smoke GREEN)**: 로컬 `pnpm --filter console-web e2e:smoke` 3/3 PASS.
- **AC-11 (CI smoke GREEN)**: 본 task impl PR 의 self-CI 에서 `frontend-e2e-smoke` job 가 platform-console-web 단계 포함 GREEN.
- **AC-12 (BE-303 3-dim verified at close chore)**: per CLAUDE.md, close chore opens only after impl PR satisfies all three dims.
- **AC-13 (BE-299 done re-stage check at close chore)**: per CLAUDE.md, `git mv ready/ → done/` + Status flip + `git add` + `git show :<done-path>` confirms `Status: done`.

# Related Specs

- `docs/adr/ADR-MONO-010-e2e-smoke-vs-full-taxonomy.md` — ACCEPTED, byte-unchanged.
- `docs/adr/ADR-MONO-011-e2e-3phase-strategy.md` — ACCEPTED, byte-unchanged.
- `projects/platform-console/specs/services/console-web/architecture.md` — byte-unchanged (smoke 가 infrastructure 만).
- `projects/platform-console/specs/contracts/console-integration-contract.md` — byte-unchanged.

# Related Contracts

- 없음 (smoke 가 producer 호출 안 함 — closed-loopback).

# Edge Cases

- **`next start` 가 prod build 의존**: smoke 실행 전 `pnpm --filter console-web build` step 필요 (ci.yml + 로컬 README). ecommerce / fan-platform 와 동일.
- **paths-filter `code-changed` filter (MONO-074/075)**: 새로 추가하는 `console-web/**` 가 markdown / docs 만 변경 case 에서 가짜 트리거 안 되도록 — 현재 `code-changed` filter 가 `*.ts/tsx/json/yml` etc. positive list. console-web 의 spec / config 파일은 모두 code-changed positive 안에 들어감 (TS/JSON). 확인은 impl 시.
- **OIDC_ISSUER_URL 미설정**: `env.OIDC_ISSUER_URL` 가 closed-loopback 으로 설정되어 `/api/auth/login` route 가 fetch 시 ECONNREFUSED. smoke 가 클릭하지 않으므로 무관.
- **isAuthenticated() 가 cookie-based**: 쿠키 없는 fresh BrowserContext → 미인증으로 즉시 fall-through. 무 backend 호출.
- **(console)/layout.tsx 의 `getCatalog()` 호출**: 미인증 redirect 가 *before* `getCatalog()` 실행 — backend 호출 없이 redirect 발화. 검증 OK.
- **로그인 페이지의 `isAuthenticated() → redirect('/console')` 분기**: 미인증 BrowserContext → fall-through → 로그인 폼 렌더. 검증 대상.

# Failure Scenarios

- **3 smoke spec 중 하나가 flake**: `retries: 1` (CI 만) + `workers: 1` + 30-s timeout 으로 deterministic. ecommerce + fan-platform 의 mature precedent 동일 설정.
- **`pnpm start` startup timeout (60 s)**: Next.js standalone server 의 prod start ≈ 5 s, 60 s 충분. cold start 가 60 s 초과 시 server 또는 build 에 문제 — 별 task.
- **paths-filter `console-web/**` 가 `code-changed` 와 결합 시 false negative (markdown-only PR 이 자동 trigger 안 됨)**: 의도된 동작 (MONO-074/075). docs-only PR 은 skip.
- **CI runner network 가 127.0.0.1:1 으로 routing**: 불가능 (127.0.0.1:1 은 reserved local 임). ecommerce / fan-platform 가 같은 패턴으로 1년 운영 무사고.
- **smoke spec 이 nightly full-stack spec 과 conflict (같은 testDir 사용)**: 다른 디렉터리 (`e2e-smoke/` vs `tests/e2e/`) + 다른 config 파일 (`playwright.smoke.config.ts` vs `playwright.config.ts`) 으로 격리. nightly 가 `tests/e2e/` 를 smoke 가 `e2e-smoke/` 를 testDir 로 선언.

# Verification

1. Spec PR diff: `git diff --stat origin/main` shows exactly **2** changed files — 본 task file + INDEX.
2. Impl PR (separate, after spec PR merges):
   - 로컬: `pnpm --filter console-web build && pnpm --filter console-web e2e:smoke` → 3/3 GREEN.
   - CI: `frontend-e2e-smoke` job 에 platform-console-web 추가된 상태로 GREEN. 본 PR 의 self-CI 에 `platform-console` paths-filter 가 트리거.
   - AC-3 / AC-4 / AC-5 grep zero diff.
   - AC-9 `parity-verification.test.ts` count = 18.
3. Close chore (after impl GREEN): BE-303 3-dim + BE-299 re-stage check.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (infrastructure-focused: smoke config + 3 spec + package.json + ci.yml job 확장 + paths-filter 1-line; pattern reuse 가 매우 높아 mechanical) / 리뷰=Opus 4.7 (dispatcher 독립 재검증 — AC-1 file count / AC-2 config shape / AC-3+4+5 byte-diff / AC-6 spec count / AC-7 script / AC-8 ci.yml + paths-filter pure-positive 검증 / AC-9 parity count / AC-10 local GREEN / AC-11 CI GREEN / AC-12+13 BE-303 + BE-299).
