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

(empty)

## done

- `TASK-PC-FE-010-console-erp-operations-section.md` — **DONE** (4-PR sequence per platform-console INDEX lifecycle, each stage objectively merge-verified `state=MERGED` + `git log origin/main` tip match). frontend-engineer(Opus). **ADR-MONO-013 § D6 Phase 6** — erp operations console section, the FIRST internal-system-primary non-GAP federation; fourth verbatim confirmation of § 3.3 zero-retrofit (FE-007 wms / FE-008 scm / FE-009 finance / **FE-010 erp** — 4 non-GAP domains, 4 traits-shape confirmation). **strictly read-only** (erp v1 has no admin-service — no operator-mutation parity; closest to FE-008 scm + FE-009 finance precedents). Spec-first cross-project gate = ERP-BE-002 (spec #655 `09d4cb2a` + impl #656 `083c744b` + close #657 `4e626fdc`) merged before promote. **spec PR #658** (squash `7d7299bc`, mergedAt 2026-05-20T05:42:00Z) — task author to backlog. **promote PR #659** (squash `2a275baa`, mergedAt 2026-05-20T05:43:45Z) — backlog→ready (prereq gate met). **impl PR #660** (squash `be9b78fa`, mergedAt 2026-05-20T06:32:31Z, +6546/-20, 46 files) — Spec-first **§ 2.4.8** = REUSE of § 2.4.5 (wms) per-domain credential rule + § 2.4.6 (scm) flat-envelope/read-only + § 2.4.7 (finance) no-fabricated-429 **verbatim** (erp = GAP OIDC access token via `getAccessToken()`, never `getOperatorToken()`; `tenant_id ∈ {erp,*}` from JWT claim, no `X-Tenant-Id`). 10 v1-live GETs (5 masters × {list, detail}: `/api/erp/masterdata/{departments,employees,job-grades,cost-centers,business-partners}`) with `?asOf=<ISO-8601>` point-in-time (E3, half-open `[from, to)`). 16 mutation endpoints + v2 `approval-service`/`read-model-service`/future `admin-service` excluded. erp flat error envelope (distinct producer, **own parser** — NOT assumed scm/finance-identical); **no 429** (erp has none — identical to finance §2.4.7, asserted absent by test grep). **UX = list-driven + asOf-first-class** (INVERSE of FE-009 finance account-id-driven shape — recorded honestly, not force-fit): `<AsOfPicker>` shared component + `useAsOf()` hook thread-through to every list/detail query (CORE E3 invariant). **erp internal-system producer obligations**: E2 active/retired both rendered visually distinct (retired NOT hidden); E1 broken/retired cross-references surfaced with badge (not silent sanitize); confidential discipline (no token/employee-PII/business-partner-financial/cost-center-sensitive logging); honest enum surfacing (RETIRED master + SEPARATED employee rendered; unknown → generic label, no throw). **§ 3 parity matrix NOT mutated** (attestation-marker count = exactly **16**; FE-006 no-drift guard unaffected — `erp-no-drift.test.ts` pins this). Code: `features/erp-ops/` (api {types/erp-api/erp-state/erp-keys} + hooks/use-erp-ops + 5 list + 5 detail + 3 shared {AsOfPicker/EffectivePeriodBadge/RetiredReferenceBadge} + ErpOpsScreen shell + barrel — 10 read fns only, NO mutation fn) + `(console)/erp` route + **GET-only** `api/erp/masterdata/**` proxy (10 routes + `_proxy.ts`, asOf pass-through) + ADR-MONO-013 § 6 Phase 6 Additive note. **per-domain-credential extended to 5 domains** (GAP=op-token / wms=GAP-OIDC / scm=GAP-OIDC / finance=GAP-OIDC / **erp=GAP-OIDC**). vitest 49 files / 540 passing (0 failed; 6 new tests + extended per-domain-credential.test.ts to 5 domains). lint 0. `pnpm build` ✓ (/erp 7.03 kB / 147 kB First Load JS within 250 kB budget; 10 erp GET proxies). **self-CI 19/19 GREEN** (run [`26145494605`](https://github.com/kanggle/monorepo-lab/actions/runs/26145494605); changes + Build & Test + 4 E2E + 5 Integration (incl. erp-integration-tests new from MONO-124) + Frontend × 3 + boot-jars × 4). **dispatcher 독립 재검증** (agent report 불신, grep/diff/build/test 재도출): scope=platform-console + ADR-MONO-013 only (0 leak across all 46 files) · `getOperatorToken` 실코드 부재 (4 매치 全 doc-comment) · 0 POST/PUT/PATCH/DELETE in api/erp/** · 0 `Idempotency-Key`/`X-Operator-Reason` 실코드 (2 매치 全 doc-comment) · 0 429/Retry-After 실코드 · erp-api.ts exported fns = exactly 10 read-only · §3=**16** · ADR-013 "Additive note" count = **4** · ADR-MONO-012 D3 canonical (### Service Type Composition H3) byte-intact. 🎯 **ADR-MONO-013 Phase 6 COMPLETE**; **Phase 7 (`console-bff` + cross-domain dashboards) gate UNGATED** (5/5 domains live: GAP + wms + scm + finance + erp). **Honest gaps recorded (no green-wash)**: (1) `BusinessPartnerDetail` renders `paymentTerms` as redacted "configured/not configured" summary (confidential discipline conservative); (2) Master detail sub-routes ([id]/page.tsx) NOT created (followed FE-009 shape — landing route /erp with all 5 lists; detail components ready as building blocks); (3) `erp-keys.ts` split (client-safe primitives separated from server-only `erp-state.ts` to prevent `next/headers` client-bundle leak). **close chore (this PR)** — `git mv review/ → done/` + Status `review → done` + INDEX move; BE-299 re-stage check verified; BE-303 객관 머지 검증 (impl PR #660 mergeCommit `be9b78fa` + `git log origin/main` tip 일치) before close chore start. closed via this close chore PR. 분석=Opus 4.7 / 구현=frontend-engineer Opus 4.7 / 리뷰=Opus 4.7 (dispatcher 독립 재검증).
- `TASK-PC-FE-009-console-finance-operations-section.md` — **DONE** (spec PR #642 squash `c49edce1` + promote chore #643 `456a6bde` + impl PR #644 `29b01826` + close chore (this) — 4-PR sequence per platform-console INDEX lifecycle, each stage objectively merge-verified `state=MERGED` + `git log origin/main` tip match). frontend-engineer(Opus). ADR-MONO-013 § D6 **Phase 5** — finance operations console section, the THIRD non-GAP federation (closes the non-GAP federation cycle: FE-007 wms → FE-008 scm → **FE-009 finance** — confirms ADR-MONO-013 § 3.3 zero-retrofit across three non-GAP domains). **strictly read-only** (finance v1 has no `admin-service` — no operator-mutation parity; closest to FE-008 scm precedent). Spec-first cross-project gate = FIN-BE-005 (spec #639 + impl #640 + close #641) merged before impl. Spec-first **§ 2.4.7** = REUSE of § 2.4.5 (wms) per-domain-credential rule + § 2.4.6 (scm) flat-envelope/read-only discipline **verbatim** (finance = GAP OIDC access token via `getAccessToken()`, never `getOperatorToken()`; `tenant_id ∈ {finance,*}` from JWT claim, no `X-Tenant-Id`). Endpoints: `GET /accounts/{id}` · `/balances` · `/transactions(paginated)` (**no list/search GET — account-id-driven, honest constraint**); finance **flat** error envelope (distinct producer, own parser·NOT wms nested); **no 429** (finance has none — not cargo-culted from scm, asserted absent by test); finance write surface + v2 `admin-service` excluded. **fintech obligations (finance analog of scm S5)**: F5 money = precision-exact minor-units **string** with `formatMoney` scale-correct rendering (no float/`Number`/`parseFloat`/`parseInt` on `amount` anywhere — on-disk source grep-asserted) + confidential/F7 (no token/PII/balance/txn/account-ref logging — console spy asserted) + honest regulated-state surfacing (FROZEN/RESTRICTED/CLOSED accounts + FAILED/REVERSED txns rendered honestly; unknown enums → generic label, no throw). **§ 3 parity matrix NOT mutated** (attestation-marker count = exactly 16; FE-006 no-drift guard unaffected — `finance-no-drift.test.ts` pins this). Code: `features/finance-ops/` (api/types/hooks/components — 3 read fns only, NO mutation fn) + `(console)/finance` server route + **GET-only** `api/finance/accounts/[accountId]/{,balances,transactions}` proxy + in-console nav. vitest 43 files / 462 passed (0 failed; new tests = finance-api / finance-proxy / finance-state / finance-nav / FinanceOpsScreen / finance-no-drift; per-domain-credential extended GAP=op-token / wms=GAP-OIDC / scm=GAP-OIDC / finance=GAP-OIDC). lint 0. `pnpm build` ✓ (/finance 4.94 kB / 145 kB First Load JS within 250 kB budget; 3 finance GET proxies added). **dispatcher 독립 재검증** (agent report 불신, grep/diff/build/test 재도출): scope=platform-console only (29 files, 0 leak) · `getOperatorToken` 실코드 부재 (4 매치 全 주석) · POST/PUT/PATCH/DELETE 부재 in api/finance/** · `Number()/parseFloat/parseInt` on amount 부재 in features/finance-ops/ · `amount: z.number()` 부재 (`z.string().regex(/^-?\d+$/)`) · 429 path 부재 · §3=**16** · canonical Identity/Service Type Composition H3 byte-intact (ADR-MONO-012 D3) · vitest 462/462 (0 regression) · lint 0 · build ✓. 🎯 **ADR-MONO-013 Phase 5 COMPLETE**; erp (Phase 6) inherits the proven non-GAP per-domain-credential + flat-envelope + read-only contract. closed via this close chore PR. 분석=Opus 4.7 / 구현=frontend-engineer Opus 4.7 / 리뷰=Opus 4.7 (dispatcher 독립 재검증).

- `TASK-PC-FE-007-console-wms-operations-section.md` — **DONE** (impl PR #633 squash `81395376`; empty dup #634 `9f5499fc` 0-delta — harmless, not reverted). frontend-engineer(Opus). ADR-MONO-013 § D6 **Phase 4 slice 1/2** — first non-GAP domain federated by the console. Spec-first `console-integration-contract.md` **§ 2.4.5** = normative **per-domain credential selection** rule (GAP = RFC 8693 exchanged operator token § 2.6 / #569 *GAP-domain-scoped* vs **wms = GAP OIDC access token direct** `getAccessToken()`, RS256/ADR-001, `tenant_id=wms` claim, no operator-exchange); wms nested error envelope; **§ 3 GAP-parity matrix byte-unchanged (16/16)**. Code `features/wms-ops/` (12 reads + 1 confirm-gated `acknowledgeAlert` idempotency-only, no `X-Operator-Reason`) + `(console)/wms` + `api/wms/**` proxy. **dispatcher 독립 재검증** (agent report 불신, git/build/test 재도출): scope=platform-console only · `getOperatorToken` 실코드 부재 · §3=16 · canonical byte-intact · vitest 342/342 · lint 0 · build ✓(/wms 147kB). closed via batch chore (this). 분석=Opus 4.7 / 구현=Opus 4.7 / 리뷰=Opus 4.7 (dispatcher 독립 재검증).

- `TASK-PC-FE-008-console-scm-operations-section.md` — **DONE** (impl PR #637 squash `c34fc0ac`; prereqs FE-007 #633 + SCM-BE-015 #635/#636 머지 후). frontend-engineer(Opus). ADR-MONO-013 § D6 **Phase 4 slice 2/2 — COMPLETES Phase 4 (FE-007 wms + FE-008 scm)**. **strictly read-only** (scm v1 = no admin-service — no operator-mutation parity). Spec-first **§ 2.4.6** *reuses* the § 2.4.5 per-domain credential rule verbatim (scm = GAP-OIDC direct like wms, never `getOperatorToken()`); scm **flat** error envelope; 429 bounded backoff (no storm); S5 `meta.warning` REQUIRED-surfaced (`<S5Warning role="alert">`, never silently dropped); **§ 3 = 16 unchanged**. Code `features/scm-ops/` (6 read fns, **NO mutation fn**) + `(console)/scm` + **GET-only** `api/scm/**`. **dispatcher 독립 재검증**: scope only · credential(`getAccessToken`, `getOperatorToken` 부재) · read-only(Idempotency-Key/X-Operator-Reason/POST/PUT/PATCH/DELETE 부재) · S5 role=alert · §3=16 · vitest 37 files 394/394 (0 regression) · lint 0 · build ✓(/scm 145kB GET-only 6 proxy). 🎯 **ADR-MONO-013 Phase 4 COMPLETE**; finance/erp (Phase 5/6) inherit the proven non-GAP per-domain-credential contract. closed via batch chore (this). 분석=Opus 4.7 / 구현=Opus 4.7 / 리뷰=Opus 4.7.

- `TASK-PC-FE-006-console-gap-operator-parity-verification.md` — impl PR #581 (squash → main `16cfdc00`). frontend-engineer(Opus). **ADR-MONO-013 Phase 2 slice 5/5 FINAL — capstone parity verification**. spec-first: `console-integration-contract.md` §3 → **16-row verified parity matrix** (capability→feature module→§2.4.x→`admin-api.md` producer §→test evidence; dashboards=ADR-015 D2 refined composed-overview "not Grafana"; §3.2 closing="Phase 2 COMPLETE; Phase 3 admin-web-retirement gate SATISFIED; retirement=별도 GAP-internal task") + `console-web/architecture.md` Phase-2-complete note + **ADR-013 §6 단일 additive blockquote**(D1–D8 불변, HARDSTOP-04). 단일 fixture `parity-matrix.ts`(16 ParityRow) ↔ `parity-verification.test.ts` **no-drift 이중강제**(test가 fixture iterate + guard가 on-disk §3 doc 마커 count==fixture len & 모든 path 존재). **honesty(no green-wash)**: test가 실제 surface서 truth 독립 재도출(verified 플래그 불신) → **gap 0, 16/16 verified, fix-task 0**. verification-only(feature/route/producer/GAP 무수정 — 머지 전 scope 기계검증). vitest 26 files/287(+89, 0 regression)·lint 0·build ✓. 🎯 **ADR-MONO-013 Phase 2 COMPLETE (5/5)**. closed via lifecycle chore PR #582. 분석=Opus 4.7 / 구현=Opus 4.7 / 리뷰=Opus 4.7 (scope+ADR-additive+no-drift 직접검증).

- `TASK-PC-FE-005-console-operator-overview-dashboards-slice.md` — impl PR #580 (squash → main `7f8d6be0`). frontend-engineer(Opus). Phase 2 slice 4/5, **ADR-MONO-015 D1-B 실현**. spec-first(§2.4.4 신규 + §3 dashboards line FE-002/003/004 스타일 재작성 + features/dashboards module). **no new producer — 기존 FE-002/003/004 server client(`searchAccounts`/`queryAudit`/`listOperators`) bounded fan-out 재사용**(중복/신규 client 無, operator-token/tenant/timeout/no-PII-log 상속). 보안핵심 `overview-api.ts` 머지 전 직접검증: **per-source isolation `isolateLeg`** = 401 ANY leg→re-throw(Promise.all reject→whole-overview re-login, no partial authed state) / 403→forbidden card / *UnavailableError→degraded card / unexpected→degraded(crash 無); read-only(전 leg GET, mutation artifact 0); bounded(3-leg Promise.all size20, 1 call-set/load, audit meta-audit 존중); all-degraded 기본값(shell never blank). vitest 25 files/198(+4, 0 regression)·lint 0·build ✓(/dashboards 142kB<250). closed via lifecycle chore PR #582. 분석=Opus 4.7 / 구현=Opus 4.7 / 리뷰=Opus 4.7.

- `TASK-PC-FE-004-console-operators-mgmt-operator-parity-slice.md` — impl PR #577 (squash → main `865c1916`). frontend-engineer(Opus). Phase 2 slice 3/5, 가장 권한 민감(operator-privilege-escalation surface). 5-op: list/create/edit-roles/change-status/change-password(self). spec-first §2.4.3 신규. **핵심 위험 = per-endpoint header matrix 비균일**: create=`X-Operator-Reason`+`Idempotency-Key` / edit-roles·change-status=reason ONLY(Idempotency-Key 부재 — 메커니즘: `idempotencyKey!==undefined`일 때만, createOperator만 전달; roles/status 테스트가 부재 단언) / change-password=self `/me/` 무. 머지 전 `operators-api.ts` 직접검증: operator-token-only(#569 차단)·tenant-gate·reason fail-safe(empty→400 pre-fetch)·**password-safe**(body만, 전용 테스트 secret 미출현, autoComplete=new-password)·§2.5 inline·encodeURIComponent·self-only password(타인설정 미발명). elevated confirm copy(create/SUPER_ADMIN grant/suspend/remove-all-roles). vitest 21 files/168(+3, 0 regression)·lint 0·build ✓. closed via lifecycle chore PR #582. 분석=Opus 4.7 / 구현=Opus 4.7 / 리뷰=Opus 4.7.

- `TASK-PC-FE-003-console-audit-security-operator-parity-slice.md` — impl PR #576 (squash → main `f0b26b14`). frontend-engineer(Opus; stream idle 1회→clean re-dispatch, dead-agent 부분 architecture.md `git checkout --` revert 후 재). Phase 2 slice 2/5. GAP audit+security **read-only** parity: 단일 producer `GET /api/admin/audit` 통합뷰(admin_actions+login_history+suspicious), `source` discriminated. spec-first §2.4.2 신규 + **FE-003 task md commit1 git-add — 이전 미커밋 갭 교정** (FE-003부터 task md git-add 명시 규칙 적용). 보안핵심 `audit-api.ts` 직접검증: operator-token-only(#569 차단, getAccessToken 미사용)·tenant-gate·**read-only 명시적 무-mutation**(X-Operator-Reason/Idempotency-Key 의도적 부재, FE-002 scaffolding 미반입)·client guard(size≤100·from>to pre-empt 422)·§2.5(401 re-login/403 PERMISSION·TENANT_SCOPE inline no-loop/503 degrade)·토큰·PII·응답 미로깅. discriminated zod union + unknown-source generic-row. vitest 17 files/119(+38, 0 regression)·lint 0·build ✓. closed via lifecycle chore PR #582. 분석=Opus 4.7 / 구현=Opus 4.7 / 리뷰=Opus 4.7.

- `TASK-PC-FE-002-console-accounts-operator-parity-slice.md` — impl PR #575 (squash → main `ad12ec4b`). frontend-engineer(Opus). Phase 2 slice 1/5. GAP accounts operator parity 8-op(search/detail/lock/unlock/bulk-lock/revoke-session/gdpr-delete/export). spec-first §2.4.1 신규(GAP accounts surface = admin-api.md per-domain cross-ref, GAP 무변경) + features/accounts module + catalog `gap`→`/accounts`. 보안핵심 `accounts-api.ts`+`_proxy.ts`+gdpr route 직접검증: operator-token-only(`getOperatorToken()` 전용, 부재→401 pre-fetch, GAP fallback 無 — #569 차단)·tenant-gate(`getActiveTenant()` 부재→400 NO_ACTIVE_TENANT, 빈헤더 無)·전 mutation reason(trim non-empty fail-safe pre-fetch)+`Idempotency-Key`(crypto.randomUUID)·destructive 확인게이트(reason capture, gdpr 이중확인 typed DELETE, bulk-lock multi-select+per-account)·§2.5 degrade·토큰/PII 미로깅. ⚠ task md origin/main 미커밋 갭(작성후 working-tree 방치, dispatch agent 가 spec+code만 커밋) — 본 lifecycle chore PR #582 가 done/ backfill 로 교정. vitest 13 files/81(+29, 0 regression)·lint 0·build ✓. closed via lifecycle chore PR #582. 분석=Opus 4.7 / 구현=Opus 4.7 / 리뷰=Opus 4.7.

- `TASK-PC-FE-002a-console-operator-token-exchange-wiring.md` — impl PR #574 (squash → main `aa7282b4`). frontend-engineer(Opus). **ADR-MONO-014 § D5 step 2** — console-side bridge + **#569 `console-integration-contract.md` §2.1↔§2.2 자기모순 fix**. spec-first(§2.1 +server-side exchange step+trust-boundary 불변식 / §2.2 L29 = operator token via exchange, producer 요구 불변 / 신규 normative §2.6 Operator Token Exchange + console-web/architecture.md auth-flow; GAP spec 무변경 cross-ref만). code: `operator-token-exchange.ts`(server-only RFC8693, body admin-api.md verbatim, 401→fail_closed/400·5xx·timeout·network·bad-tokenType→unavailable, 토큰 로깅 0, GAP fallback 절대 無) + `session.ts`(OPERATOR_COOKIE/getOperatorToken, isAuthenticated=GAP+operator) + callback(exchange 후 store; **실패 시 GAP 쿠키까지 삭제** → partial-authed 無, #569 차단) + refresh(re-exchange ADR-014 D2; 실패→전세션 drop 401) + logout(operator 쿠키 clear) + registry-client(`getOperatorToken()` — #569 코드 fix). 3대 보안임계 머지 전 직접검증. ⚠ task md 미커밋 갭(FE-002 동일) — PR #582 가 done/ backfill 교정. vitest 9 files/45·lint 0·build ✓. closed via lifecycle chore PR #582. 분석=Opus 4.7 / 구현=Opus 4.7 / 리뷰=Opus 4.7.

- `TASK-PC-FE-001-console-web-shell-gap-sso.md` — impl PR #569 (squash → main `debc6849`). frontend-engineer(Opus) — ADR-MONO-013 Phase 1→2 bridge. 선행 GAP `TASK-BE-296`(#568). Layered-by-Feature 셸: GAP OIDC Auth Code+PKCE server routes(`app/api/auth/{login,callback,refresh,logout,session}`+`shared/lib/{pkce,session}`) HttpOnly·Secure·SameSite=strict 쿠키·public client·401→server refresh + data-driven 카탈로그(features/catalog, `available:false`→coming-soon, 코드변경 0) + 테넌트 스위처 + resilience + a11y(WCAG AA, axe)+web-vitals+perf budget. 경로 정렬(.env/compose/contract → BE-296 권위 `http://gap.local/api/admin/console/registry`). 오케스트레이터 2-fix(stream-timeout 유실→직접검증: auth-routes.test vi.hoisted TDZ / ServiceTile aria-disabled→div role=group). `pnpm build` 7-route 99.9KB·lint 0·vitest 6/6 suites 26/26. scope=platform-console only. closed via lifecycle chore PR #582. 분석=Opus 4.7 / 구현=Opus 4.7 / 리뷰=Opus 4.7.

## archive

(empty)
