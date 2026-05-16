# Tasks Index — platform-console

This document defines task lifecycle, naming, and move rules for the **platform-console** project. Repo-root [tasks/INDEX.md](../../../tasks/INDEX.md) covers monorepo-level (cross-project) tasks; this file covers platform-console-internal tasks only.

---

# Lifecycle

backlog → ready → in-progress → review → done → archive

Only tasks in `ready/` may be implemented.

---

# Task Types

- `TASK-PC-FE-XXX`: frontend (`console-web` Next.js implementation)
- `TASK-PC-BE-XXX`: backend (`console-bff` — ADR-MONO-013 Phase 7, deferred)
- `TASK-PC-INT-XXX`: cross-service / cross-project integration / E2E (Testcontainers · Docker compose · federated domain APIs)

> Cross-project prerequisites that live in **another project** (e.g. GAP OIDC client / registry) are tracked as that project's task (e.g. `TASK-BE-296` in `global-account-platform`), referenced from the dependent `TASK-PC-*`.

---

# Move Rules

## backlog → ready
Allowed only when:
- related specs exist (`specs/services/<service>/architecture.md`, `specs/contracts/...`)
- related contracts are identified
- acceptance criteria are clear
- task template is complete
- cross-project prerequisite tasks (if any) are identified and linked

## ready → in-progress
Allowed only when implementation starts.

## in-progress → review
Allowed only when:
- implementation is complete
- tests are added
- contract / spec updates are completed if required

## review → done
Allowed only after review approval.

### Review Rules
- Tasks in `review/` must not be re-implemented directly.
- If a review reveals a bug or missing requirement, create a new fix task in `ready/` referencing the original task.
- Fix tasks must include the original task ID in their Goal section.
- Do not modify a task file after it moves to `review/` or `done/`.

### PR Separation Rule (lifecycle ↔ PR boundary)

| Stage | Recommended PR shape |
|---|---|
| `(writing) → ready` | **spec PR** — adds the task file to `ready/` + updates this `INDEX.md` ready list. No implementation code. |
| `ready → in-progress → review` | **impl PR** — moves the task through `in-progress/` to `review/` and lands the implementation. |
| `review → done` | **chore PR** — moves merged task file(s) from `review/` to `done/` + updates the `INDEX.md` done list. May batch. |

The repo-root [tasks/INDEX.md](../../../tasks/INDEX.md) is the authoritative definition. This summary applies the same rule at the project level.

## done → archive
Allowed when no further active change is expected.

---

# Rule

Tasks must not be implemented from `backlog/`, `in-progress/`, `review/`, `done/`, or `archive/`.

---

# Task List

## backlog

(empty)

## ready

(empty)

## in-progress

(empty)

## review

- `TASK-PC-FE-001-console-web-shell-gap-sso.md` — impl `task/pc-fe-001-console-web-shell-gap-sso` (off main `e9b6fdb1`, BE-296 머지 후). frontend-engineer(Opus) — ADR-MONO-013 Phase 1→2 bridge. **선행 충족**: GAP `TASK-BE-296` (#568 머지). Layered-by-Feature 셸: GAP OIDC **Auth Code + PKCE** server routes (`app/api/auth/{login,callback,refresh,logout,session}` + `shared/lib/pkce.ts`·`session.ts`) — HttpOnly·Secure·SameSite=strict 쿠키, public client(secret 없음), 401→server refresh. **data-driven 카탈로그** (`features/catalog`, registry-client/types, `available:false`→"coming soon", 코드변경 0). **테넌트 스위처** (`features/tenant`). resilience(registry timeout→degraded). **경로 정렬**: `.env.example`/`docker-compose.yml`/`.env.local.example`/`console-integration-contract.md` 를 BE-296 권위 경로 `http://gap.local/api/admin/console/registry` 로 수정(placeholder `/internal/console/registry` supersede). a11y(WCAG AA, axe) + web-vitals + perf budget(login 109KB/console 132KB ≤ 180/250). **검증**: `pnpm build` 성공(라우트 7, First Load 99.9KB) · `pnpm lint` ✔ 0 · `pnpm exec vitest run` **6/6 suites · 26/26 tests PASS** (auth-routes/use-catalog/TenantSwitcher/ServiceCatalog/registry-contract/tenant-isolation). **오케스트레이터 2-fix** (에이전트 보고 stream-timeout 유실 → 직접 검증): (1) `auth-routes.test.ts` `vi.mock` factory TDZ → `vi.hoisted()` ; (2) `ServiceTile` `aria-disabled` on `<li>`(role listitem 미지원, jsx-a11y) → 내부 `<div role="group" aria-disabled>` 로 이전(+testid 동반, ServiceCatalog 단언 유지). scope=`projects/platform-console/` only. E2E(Playwright)는 services 필요 → CI/manual. lifecycle = ready → review (in-progress 우회, single-PR). 분석=Opus 4.7 / 구현=Opus 4.7 (frontend-engineer) / 검증·fix=Opus 4.7 (orchestrator).

## done

(empty)

## archive

(empty)
