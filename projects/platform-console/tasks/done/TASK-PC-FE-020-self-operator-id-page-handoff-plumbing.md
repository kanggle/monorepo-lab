# Task ID

TASK-PC-FE-020

# Title

platform-console `selfOperatorId` page-handoff plumbing — server-side resolve the caller's own `operatorId` via `GET /api/admin/me` and pass it to `OperatorsScreen` so the admin profile-edit per-row "프로파일 편집" button is disabled on the self row (UX gate activation; closes TASK-PC-FE-017 honest gap (b))

# Status

done

# Owner

frontend

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- test

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

- **depends on**: TASK-PC-FE-017 (console-web admin profile-edit per-row UI, **DONE** 2026-05-22). `OperatorsScreen` 가 이미 `selfOperatorId?: string | null` prop 을 받고 (`OperatorsScreen.tsx:90`) self-row 의 "프로파일 편집" 버튼 `disabled` 조건에 활용 중 (`OperatorsScreen.tsx:490-495`). 현재 `(console)/operators/page.tsx` 가 prop 을 pass 하지 않음 → default `null` → gate inactive.
- **depends on (indirectly)**: TASK-BE-308 (`GET /api/admin/operators` per-item operatorContext extension, **DONE** 2026-05-22) — `/api/admin/me` 가 동일 `OperatorSummaryResponse` shape 를 emit 하므로 BE-308 이후의 operatorContext field 가 me-call 응답에도 자동 포함; 본 task 는 그 중 `operatorId` 만 사용.
- **origin**: TASK-PC-FE-017 § Honest gaps (b): *"`selfOperatorId` prop 은 plumbed 됐으나 page handoff 가 현재 null 전달 (producer-side `SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH` 가 fail-safe). 1-line page 변경 + session helper 가 caller's `operatorId` 노출 시 self-row UI gate 활성화."* 본 task 는 그 deferred follow-up 종결.
- **prerequisite for**: nothing.
- **spec-first**: spec PR (this file + INDEX) → impl PR (`getSelfOperator()` server helper + `page.tsx` 1-2 line 변경 + vitest) → close chore PR.
- **no ADR** (HARDSTOP-09 not triggered): pure consumer adoption of an existing producer endpoint (`GET /api/admin/me`, already consumed by every authenticated console session implicitly via cookies). No architectural decision; no permission change; no header matrix change; no parity matrix row addition (parity matrix tracks operator-management *mutations*, not server-side reads internal to the page composition).

---

# Goal

`OperatorsScreen` 의 self-row UI gate 가 PC-FE-017 에서 plumbed 됐지만 page handoff 가 `null` 이라 현재 inactive. 결과: SUPER_ADMIN 이 자기 자신의 row 에서 "프로파일 편집" 버튼을 클릭할 수 있고, 클릭하면 dialog open → 입력 후 Save → producer 가 `400 SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH` 로 거부. fail-safe 는 작동하지만 UX 가 "버튼 누르고 dialog 채우고 거부됨" 의 우회 경로 — 명확하지 않음.

활성화:

1. **서버 helper `getSelfOperatorIdOrNull(): Promise<string | null>`** — `GET /api/admin/me` 를 호출하고 응답의 `operatorId` 를 반환. 모든 실패 (401 / 403 / 503 / timeout / network / schema parse) 에서 `null` 반환 (UX gate 의 fail-safe — producer 400 이 권위; gate 가 active 못해도 가는 fail-closed 가 아닌 fail-graceful).
2. **`(console)/operators/page.tsx`** — 기존의 `getOperatorsListState()` + `getCatalog()` 호출에 더하여 `getSelfOperatorIdOrNull()` 호출 결과를 `<OperatorsScreen selfOperatorId={...} />` 로 pass.
3. **Tests** — vitest unit case: helper 가 success 시 operatorId / 401/403/503/network 시 null 반환. Page-level 통합 테스트가 있다면 page 가 prop 을 forward 하는지 검증 (없으면 helper unit 만으로 충분).

After this task, self row 의 "프로파일 편집" 버튼이 명확하게 disabled 로 렌더 — SUPER_ADMIN 이 자기 자신의 profile 변경은 `/operators` 의 self-serve `MyProfileForm` 영역에서 수행해야 한다는 게 UX 로 분명.

# Decision authority (why /api/admin/me round-trip vs JWT decode, why fail-graceful, why no parity row, why no schema change)

- **Why `GET /api/admin/me` round-trip (NOT JWT `sub` decode)**:
  - Producer 가 권위: operator token 의 `sub` claim 이 admin-service 의 `operator_id` 와 같다는 invariant 은 producer-side guarantee. 그러나 console-web 이 `sub` 를 직접 decode 하면 (a) unverified-signature decode 패턴을 codebase 에 도입 (security-sensitive 의존성 + 미래 token format 변경 시 fragile) (b) JWT 라이브러리 (`jose`) 새 의존 추가가 1-line 가치 대비 과대.
  - 기존 path: console-web 의 모든 `/api/admin/**` 호출이 같은 cookie 로 producer 에 가서 GAP 가 `OperatorAuthenticationFilter` 에서 token verify + `sub` resolve. `/api/admin/me` 호출도 같은 path → producer 가 동일 invariant 보장.
  - Round-trip cost: page 가 이미 `getOperatorsListState()` + `getCatalog()` 두 호출 수행 중. +1 호출 (서버-내부, low-latency localhost-side admin-service) 의 부담 미미. 페이지 로드 routes 가 `force-dynamic` 이라 캐시 layer 검토 불요.
- **Why fail-graceful (`null` on any failure, NOT redirect / error page)**:
  - Operators list 가 이미 `redirect('/login')` 을 401 시 수행. self-id resolve 가 그 후 (already-authed-state) 에 일어남 → 401 가능성 매우 낮음. 그러나 만약 일어나도 helper 가 null 반환 → page 가 normal 렌더 (gate inactive but everything else works); producer 400 이 self-via-admin-path 의 권위 fail-safe.
  - Producer 가 권위인 보안 invariant 를 console 쪽 helper 의 정상 작동에 의존시키면 fragile. fail-graceful 가 robust 선택.
- **Why no parity matrix row addition**:
  - `/api/admin/me` 는 console-integration-contract.md § 2.4.3 ops table 의 *operator-management* 표면 (list / create / edit-roles / change-status / change-password / change-profile / admin-set-profile) 에 명시 안 됨 — 이건 *page-level session resolution* 의 internal read 로, parity-matrix 가 추적하는 operator-management mutation 의 일부가 아님. me-call 은 모든 authenticated console session 의 implicit infrastructure (당장 catalog + tenant resolution 과 같은 layer).
  - § 3.1 parity matrix 가 추적하는 "operations" 는 *user-facing operator-management UX* — me-resolve 는 그 외 page-composition internal. row 추가는 over-tracking.
- **Why no schema change to `OperatorSummarySchema`**:
  - `GET /api/admin/me` 와 `GET /api/admin/operators` 의 item 가 동일 `OperatorSummaryResponse` shape 를 producer 가 반환 (admin-api.md). 동일 zod schema 재사용. me-call 의 응답만 따로 새 schema 만들 이유 없음.
- **Why no console-integration-contract.md 변경**:
  - 본 task 는 page-composition 의 self-id resolve 가 어떻게 일어나는지 internal infrastructure 만 다룸. console-integration-contract.md 는 *user-facing parity* + producer cross-reference 가 목표. me-call 은 이미 producer admin-api.md § GET /api/admin/me 에 존재; 별도 cross-reference 불필요.

---

# Scope

## In Scope

**Specs (spec PR — this PR)**:

- This task file.
- `projects/platform-console/tasks/INDEX.md` — ready entry.

**Code (impl PR — out of scope here, listed for the dispatch agent to know the shape)**:

- `projects/platform-console/apps/console-web/src/features/operators/api/operators-api.ts` (or sibling `operators-state.ts` — chosen at impl based on path coherence):
  - Add `getSelfOperatorIdOrNull(): Promise<string | null>` — calls `callGapOperators({ method: 'GET', path: '/api/admin/me' }, OperatorSummarySchema.parse)` (or equivalent `callGapAdminMe` shape if there's a more natural site) → returns `.operatorId`. Catches ALL exceptions (`ApiError`, `OperatorsUnavailableError`, network, schema parse) and returns `null` — fail-graceful gate de-activation, never breaks the page.
- `projects/platform-console/apps/console-web/src/features/operators/index.ts` — export the new helper (or its parent module — match existing export convention).
- `projects/platform-console/apps/console-web/src/app/(console)/operators/page.tsx`:
  - Call `await getSelfOperatorIdOrNull()` (parallel to existing `await getCatalog()` if the runtime supports, OR sequential — micro-optimization, choose at impl).
  - Pass the result as `<OperatorsScreen ... selfOperatorId={selfOperatorId} />`.
- **Tests** (impl PR):
  - `apps/console-web/tests/unit/features/operators/self-operator-id.test.ts` (new): 4-5 cases — (a) success → operatorId; (b) 401 → null; (c) 403 → null; (d) 503/network/timeout → null; (e) schema parse fail → null.
  - No new Playwright e2e (mirrors PC-FE-016/017/018 deferral pattern). The UX gate is verified via existing slice (OperatorsScreen tests can be extended OR existing PC-FE-017 self-row disable tests work via the prop directly — no new page-level test needed).

## Out of Scope

- **JWT `sub` decode helper / `jose` dependency** — explicitly rejected (see § Decision authority).
- **`OperatorAdminController.getCurrentOperator` / `/api/admin/me` producer change** — none. Producer is already correct and exposes `operatorId`.
- **`OperatorSummarySchema` 변경** — none. Same shape as list items.
- **Caching the me-call result** (e.g. server-side memo per request) — micro-optimization out of scope; the call is per-page-load and lightweight. If profiling shows it as a hot path later, add memo then (KISS now).
- **Surface me-call result to other pages** (e.g. `(console)/dashboards/overview`) — out of scope. Only `/operators` needs it for the self-row UI gate. Other pages can opt-in if a future feature needs it.
- **Parity matrix row addition** — rejected (see § Decision authority).
- **`console-integration-contract.md` 변경** — none (see § Decision authority).
- **ADR amendment** — none.

# Acceptance Criteria

- **AC-1 (spec PR atomic)**: spec PR lands exactly **2 files** — this task file + INDEX. No code; no contract; no parity matrix.
- **AC-2 (impl PR — sequential, ≤4 files)**: impl PR lands the new helper + page edit + new test file + (possibly) index export. Verified ≤ 5 files. Atomic.
- **AC-3 (helper fail-graceful)**: `getSelfOperatorIdOrNull()` returns `string` on success and `null` on every observed failure mode (401 / 403 / 503 / timeout / network / schema parse / unexpected). Test cases (a)-(e) verify this.
- **AC-4 (page prop wired)**: `(console)/operators/page.tsx` passes the resolved (or null) value as `selfOperatorId` to `<OperatorsScreen />`. `OperatorsScreen` existing logic (`OperatorsScreen.tsx:490-495`) already gates the per-row button.
- **AC-5 (no producer change)**: 0 byte diff across `projects/global-account-platform/` in the impl PR.
- **AC-6 (no console-bff change)**: 0 byte diff across `projects/platform-console/apps/console-bff/src/**` in the impl PR. ADR-MONO-017 D2 + § 2.4.9 hard invariant preserved.
- **AC-7 (zero-retrofit other producers)**: 0 byte diff across `projects/{wms,scm,finance,erp,fan,ecommerce}-platform/`. **13회째 confirmation**.
- **AC-8 (parity matrix count unchanged)**: `parity-verification.test.ts` expected count remains **18**.
- **AC-9 (BE-303 3-dim verified at close chore)**: per CLAUDE.md, close chore opens only after impl PR satisfies all three dims.
- **AC-10 (BE-299 done re-stage check at close chore)**: per CLAUDE.md, `git mv ready/ → done/` + Status flip + `git add` + `git show :<done-path>` confirms `Status: done`.

# Related Specs

- `projects/global-account-platform/specs/contracts/http/admin-api.md § GET /api/admin/me` — **byte-unchanged**. Producer endpoint already exists; this task consumes it server-side.
- `projects/platform-console/specs/contracts/console-integration-contract.md` — **byte-unchanged**. Internal page-composition read; not a parity surface (see § Decision authority).
- `docs/adr/ADR-MONO-017-platform-console-bff-architecture.md` — **byte-unchanged**.

# Related Contracts

- `projects/global-account-platform/specs/contracts/http/admin-api.md § GET /api/admin/me` — producer contract consumed (already on main).

# Edge Cases

- **GAP `/api/admin/me` returns 401** (operator token expired between cookie set and request) → helper catches `ApiError(status=401)` → returns null. Page renders with gate inactive; the next mutation will surface 401 → existing redirect-to-login flow.
- **GAP returns 403** (the caller somehow lost `operator.manage` between login and now) → returns null. The list call will also return 403 in the same render — the page's existing `permissionError` branch renders the "not permitted" state; gate is inactive but irrelevant.
- **GAP returns 503 / network timeout** → returns null. The list call's degraded branch likely also triggers — page renders the degraded notice.
- **GAP returns valid response but `OperatorSummarySchema.parse` fails** (unexpected producer drift) → returns null. The drift will also affect the list response; that's the louder signal. Helper stays silent fail-graceful.
- **The me-call succeeds but `operatorId` is empty string** (defensive against producer bug) → returns the empty string verbatim (the `selfOperatorId === op.operatorId` check still works against any row's operatorId which is also a non-empty string → no row matches → gate inactive for everyone, same as null).
- **Caller's operatorId differs from `OperatorAuthenticationFilter` resolved JWT `sub`** (impossible in practice — producer guarantee) → producer is the authority; helper returns whatever producer says. No client-side fallback to decode.

# Failure Scenarios

- **Helper throws instead of returning null** → AC-3 fails. Reject in review. Every catch path must return null.
- **Page redirects to login on me-call failure** → over-aggressive. The list call's existing 401 handling is already the redirect path; helper failure must be silent.
- **Helper memoizes the result across requests** (e.g. module-level cache) → leaks operatorId across tenants/users. Reject; per-request invocation only.
- **Reviewer suggests adding a parity matrix row for "me"** → reject. me-call is page-composition internal, not user-facing operator-management UX (see § Decision authority).
- **Reviewer suggests JWT `sub` decode for speed** → reject. Round-trip is sufficient; no new dep.
- **Page omits the new prop wiring** → AC-4 fails. The whole point of the task.

# Verification

1. Spec PR diff: `git diff --stat origin/main` shows exactly **2** changed files.
2. Impl PR (separate): `pnpm --filter console-web test` GREEN with the new helper test file; `pnpm --filter console-web build` GREEN.
3. AC-3 fail-graceful: explicit cases for 401 / 403 / 503 / network / parse all return null.
4. AC-5 / AC-6 / AC-7 grep zero diff outside `projects/platform-console/apps/console-web/`.
5. Self-CI GREEN at impl-PR merge time; BE-303 3-dim verified at close chore start.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (mechanical helper + 1-2 line page change + vitest; no net-new judgement) / 리뷰=Opus 4.7 (dispatcher 독립 재검증 — AC-1 file count / AC-3 fail-graceful cases / AC-4 page prop wired / AC-5+6+7 byte-diff grep / AC-9 BE-303 3-dim / AC-10 BE-299 done re-stage).
