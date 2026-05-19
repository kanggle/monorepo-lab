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

- `TASK-PC-FE-008-console-scm-operations-section.md` — **impl PR (review)**. frontend-engineer(Opus). ADR-MONO-013 § D6 **Phase 4 slice 2/2 — the SECOND non-GAP domain federated by the console; COMPLETES Phase 4 (FE-007 wms + FE-008 scm)**. **STRICTLY READ-ONLY** (scm has no `admin-service` at v1 — no operator-mutation parity). **Spec-first (same PR, before/with code)**: `console-integration-contract.md` **§ 2.4.6** added (scm read-only binding cross-ref to `procurement-api.md` PO list/detail + `inventory-visibility-api.md` snapshot/sku/staleness/nodes; **REUSES the § 2.4.5 per-domain credential rule verbatim — does NOT re-derive/redefine it**; scm = GAP OIDC access token direct (`getAccessToken()`, RS256/ADR-001, `tenant_id ∈ {scm,*}` JWT claim, NEVER `getOperatorToken()` — #569 GAP-domain-scoped, same outcome as wms); scm **flat** error envelope `{ code, message, timestamp }` (distinct from wms's nested shape); 429 `RATE_LIMIT_EXCEEDED` (`Retry-After: 1`) → bounded backoff (NO storm); S5 `meta.warning` surfacing obligation; `X-Cache`/staleness honesty; scm-side spec-first basis = merged scm `gateway-public-routes.md` § *platform-console operator read consumer* / TASK-SCM-BE-015) — **§ 3 GAP-parity matrix byte-unchanged** (scm is additive domain scope, not a parity row; the `verified by TASK-PC-FE-006` no-drift marker stays exactly **16/16** — §2.4.6 prose deliberately avoids that phrase) + `console-web/architecture.md` `features/scm-ops` module + `(console)/scm` route + `api/scm/**` **GET-only** proxy + § Integration/Testing/References (canonical Identity table + `### Service Type Composition` H3 **byte-intact**, ADR-MONO-012 D3 — verified `git diff` shows no +/- on those lines). scm/GAP specs unchanged (cross-reference only — scope `git status` verified = platform-console only). **Code**: `features/scm-ops/` (api: `getAccessToken()` server-side — **never** `getOperatorToken()` (grep-verified absent in scm-ops + 0 in api/scm proxy), AbortController hard timeout, scm **flat**-envelope parser (a wms-nested parser yields synthetic HTTP_4xx — pinned), **ONE bounded 429 retry then surface — storm-guarded by a `retried` latch**, `X-Cache`/staleness surfaced honestly, S5 `meta.warning` a REQUIRED transform-defaulted view-model field (never silently dropped even on a producer omission); 6 read fns: PO list/detail + IV snapshot/sku/staleness/nodes — **NO mutation fn at all** (api module export set asserted); zod tolerant of unknown PO/node status) + read-only hooks (no mutation hook, no tight refetch, `retry:false`) + components (PO list filters/pagination + read-only PO detail dialog (only a 닫기 button — no submit/confirm/cancel) + IV snapshot table + per-SKU breakdown + node staleness panel; **every IV view renders the S5 warning prominently via `<S5Warning role="alert">`**; STALE/UNREACHABLE nodes shown honestly; WCAG AA axe-clean) + `(console)/scm` server component (registry-driven eligibility gate — no cross-tenant call fabricated; 401→whole-session re-login / 403→inline / 429→rate-limited notice / 503·timeout→scm-section-only degrade, shell + GAP/wms intact) + `api/scm/**` same-origin **GET-only** proxy (no PO-write/webhook/Idempotency-Key/X-Operator-Reason route) + in-console nav (`/scm`). **Tests**: 5 new files (`scm-api` 21, `scm-proxy`, `scm-state`, `scm-nav`, `ScmOpsScreen`) + extended `per-domain-credential.test.ts` to the **3-domain cross-domain regression** (GAP=operator-token / wms=GAP-OIDC / scm=GAP-OIDC — wms.Authorization === scm.Authorization, both ≠ GAP's). **Gates**: `pnpm build` ✓ (`/scm` 145 kB gz < 250 budget, in the 144–149 kB sibling band — no perf regression; 6 `/api/scm/**` GET proxies, no mutation route), `pnpm lint` 0, `pnpm exec vitest run` **394/394 (37 files; +52 tests across 5 new scm suites + the extended 3-domain regression; 0 regression — FE-001..007 all green, GAP still operator-token, wms still GAP-OIDC, scm GAP-OIDC, gap/wms routes unchanged, scm nav/route resolves)**, axe WCAG AA clean. Scope = `projects/platform-console/` only (scm/GAP/shared-path untouched — `git status`/`git diff --stat` verified). 🎯 **ADR-MONO-013 Phase 4 COMPLETE (FE-007 + FE-008)**; finance/erp (Phase 5/6) inherit the proven non-GAP per-domain-credential contract. Recommended impl model **Opus** (contract-extension + cross-project spec dependency). 분석=Opus 4.7 / 구현=Opus 4.7.

- `TASK-PC-FE-007-console-wms-operations-section.md` — **impl PR (review)**. frontend-engineer(Opus). ADR-MONO-013 § D6 **Phase 4 slice 1/2** — the **first non-GAP** domain federated by the console. **Spec-first (same PR, before/with code)**: `console-integration-contract.md` **§ 2.4.5** added (wms binding cross-ref to `admin-service-api.md` § 1 read-model 12 reads + `alerts/{id}/acknowledge` + `operations/projection-status`; the normative **per-domain credential selection** rule — GAP = RFC 8693 exchanged operator token (§ 2.6 / #569 *GAP-domain-scoped*) vs **wms = GAP OIDC access token directly** (`getAccessToken()`, RS256/ADR-001, `tenant_id=wms` JWT claim, no operator-exchange); tenant-model divergence (JWT claim vs `X-Tenant-Id`); wms **nested** error envelope `{ error: { code … } }`; § 2.5 resilience; read-model-lag honesty) — **§ 3 GAP-parity matrix byte-unchanged** (wms is additive domain scope, not a parity row; verified by the `parity-verification` no-drift guard staying 16/16) + `console-web/architecture.md` `features/wms-ops` module + `(console)/wms` route + `api/wms/**` proxy + § Auth Flow per-domain-credential rule (canonical Identity table + `### Service Type Composition` H3 byte-intact, ADR-MONO-012 D3). wms/GAP specs unchanged. **Code**: `features/wms-ops/` (api: `getAccessToken()` server-side — **never** `getOperatorToken()` (grep-verified absent), AbortController hard timeout, wms nested-envelope parser, `X-Read-Model-Lag-Seconds` surfaced, 12 reads + one confirm-gated `acknowledgeAlert` with `Idempotency-Key` only (stable per confirmed action / fresh per attempt), no `X-Operator-Reason`, empty body) + hooks + components (inventory snapshot table/filters/pagination, append-only adjustments read-only, alerts confirm-gated ack, read-model-lag banner, WCAG AA) + `(console)/wms` server component (registry-driven eligibility gate — no cross-tenant call fabricated) + `api/wms/**` same-origin proxy + in-console nav. **Gates**: `pnpm build` ✓ (`/wms` 147 kB gz < 250 budget, no perf regression vs sibling 144–149 kB), `pnpm lint` 0, `pnpm exec vitest run` **342/342 (32 files; +55 new across 6 wms suites incl. the per-domain-credential both-sides regression; 0 regression — GAP path STILL operator-token, `gap.baseRoute` unchanged, wms nav/route resolves)**, axe WCAG AA clean. Scope = `projects/platform-console/` only (wms/GAP/shared-path untouched — `git diff --stat` verified). Recommended impl model **Opus** (contract-extension; security-sensitive auth-divergence). 분석=Opus 4.7 / 구현=Opus 4.7.

## done

- `TASK-PC-FE-006-console-gap-operator-parity-verification.md` — impl PR #581 (squash → main `16cfdc00`). frontend-engineer(Opus). **ADR-MONO-013 Phase 2 slice 5/5 FINAL — capstone parity verification**. spec-first: `console-integration-contract.md` §3 → **16-row verified parity matrix** (capability→feature module→§2.4.x→`admin-api.md` producer §→test evidence; dashboards=ADR-015 D2 refined composed-overview "not Grafana"; §3.2 closing="Phase 2 COMPLETE; Phase 3 admin-web-retirement gate SATISFIED; retirement=별도 GAP-internal task") + `console-web/architecture.md` Phase-2-complete note + **ADR-013 §6 단일 additive blockquote**(D1–D8 불변, HARDSTOP-04). 단일 fixture `parity-matrix.ts`(16 ParityRow) ↔ `parity-verification.test.ts` **no-drift 이중강제**(test가 fixture iterate + guard가 on-disk §3 doc 마커 count==fixture len & 모든 path 존재). **honesty(no green-wash)**: test가 실제 surface서 truth 독립 재도출(verified 플래그 불신) → **gap 0, 16/16 verified, fix-task 0**. verification-only(feature/route/producer/GAP 무수정 — 머지 전 scope 기계검증). vitest 26 files/287(+89, 0 regression)·lint 0·build ✓. 🎯 **ADR-MONO-013 Phase 2 COMPLETE (5/5)**. closed via lifecycle chore PR #582. 분석=Opus 4.7 / 구현=Opus 4.7 / 리뷰=Opus 4.7 (scope+ADR-additive+no-drift 직접검증).

- `TASK-PC-FE-005-console-operator-overview-dashboards-slice.md` — impl PR #580 (squash → main `7f8d6be0`). frontend-engineer(Opus). Phase 2 slice 4/5, **ADR-MONO-015 D1-B 실현**. spec-first(§2.4.4 신규 + §3 dashboards line FE-002/003/004 스타일 재작성 + features/dashboards module). **no new producer — 기존 FE-002/003/004 server client(`searchAccounts`/`queryAudit`/`listOperators`) bounded fan-out 재사용**(중복/신규 client 無, operator-token/tenant/timeout/no-PII-log 상속). 보안핵심 `overview-api.ts` 머지 전 직접검증: **per-source isolation `isolateLeg`** = 401 ANY leg→re-throw(Promise.all reject→whole-overview re-login, no partial authed state) / 403→forbidden card / *UnavailableError→degraded card / unexpected→degraded(crash 無); read-only(전 leg GET, mutation artifact 0); bounded(3-leg Promise.all size20, 1 call-set/load, audit meta-audit 존중); all-degraded 기본값(shell never blank). vitest 25 files/198(+4, 0 regression)·lint 0·build ✓(/dashboards 142kB<250). closed via lifecycle chore PR #582. 분석=Opus 4.7 / 구현=Opus 4.7 / 리뷰=Opus 4.7.

- `TASK-PC-FE-004-console-operators-mgmt-operator-parity-slice.md` — impl PR #577 (squash → main `865c1916`). frontend-engineer(Opus). Phase 2 slice 3/5, 가장 권한 민감(operator-privilege-escalation surface). 5-op: list/create/edit-roles/change-status/change-password(self). spec-first §2.4.3 신규. **핵심 위험 = per-endpoint header matrix 비균일**: create=`X-Operator-Reason`+`Idempotency-Key` / edit-roles·change-status=reason ONLY(Idempotency-Key 부재 — 메커니즘: `idempotencyKey!==undefined`일 때만, createOperator만 전달; roles/status 테스트가 부재 단언) / change-password=self `/me/` 무. 머지 전 `operators-api.ts` 직접검증: operator-token-only(#569 차단)·tenant-gate·reason fail-safe(empty→400 pre-fetch)·**password-safe**(body만, 전용 테스트 secret 미출현, autoComplete=new-password)·§2.5 inline·encodeURIComponent·self-only password(타인설정 미발명). elevated confirm copy(create/SUPER_ADMIN grant/suspend/remove-all-roles). vitest 21 files/168(+3, 0 regression)·lint 0·build ✓. closed via lifecycle chore PR #582. 분석=Opus 4.7 / 구현=Opus 4.7 / 리뷰=Opus 4.7.

- `TASK-PC-FE-003-console-audit-security-operator-parity-slice.md` — impl PR #576 (squash → main `f0b26b14`). frontend-engineer(Opus; stream idle 1회→clean re-dispatch, dead-agent 부분 architecture.md `git checkout --` revert 후 재). Phase 2 slice 2/5. GAP audit+security **read-only** parity: 단일 producer `GET /api/admin/audit` 통합뷰(admin_actions+login_history+suspicious), `source` discriminated. spec-first §2.4.2 신규 + **FE-003 task md commit1 git-add — 이전 미커밋 갭 교정** (FE-003부터 task md git-add 명시 규칙 적용). 보안핵심 `audit-api.ts` 직접검증: operator-token-only(#569 차단, getAccessToken 미사용)·tenant-gate·**read-only 명시적 무-mutation**(X-Operator-Reason/Idempotency-Key 의도적 부재, FE-002 scaffolding 미반입)·client guard(size≤100·from>to pre-empt 422)·§2.5(401 re-login/403 PERMISSION·TENANT_SCOPE inline no-loop/503 degrade)·토큰·PII·응답 미로깅. discriminated zod union + unknown-source generic-row. vitest 17 files/119(+38, 0 regression)·lint 0·build ✓. closed via lifecycle chore PR #582. 분석=Opus 4.7 / 구현=Opus 4.7 / 리뷰=Opus 4.7.

- `TASK-PC-FE-002-console-accounts-operator-parity-slice.md` — impl PR #575 (squash → main `ad12ec4b`). frontend-engineer(Opus). Phase 2 slice 1/5. GAP accounts operator parity 8-op(search/detail/lock/unlock/bulk-lock/revoke-session/gdpr-delete/export). spec-first §2.4.1 신규(GAP accounts surface = admin-api.md per-domain cross-ref, GAP 무변경) + features/accounts module + catalog `gap`→`/accounts`. 보안핵심 `accounts-api.ts`+`_proxy.ts`+gdpr route 직접검증: operator-token-only(`getOperatorToken()` 전용, 부재→401 pre-fetch, GAP fallback 無 — #569 차단)·tenant-gate(`getActiveTenant()` 부재→400 NO_ACTIVE_TENANT, 빈헤더 無)·전 mutation reason(trim non-empty fail-safe pre-fetch)+`Idempotency-Key`(crypto.randomUUID)·destructive 확인게이트(reason capture, gdpr 이중확인 typed DELETE, bulk-lock multi-select+per-account)·§2.5 degrade·토큰/PII 미로깅. ⚠ task md origin/main 미커밋 갭(작성후 working-tree 방치, dispatch agent 가 spec+code만 커밋) — 본 lifecycle chore PR #582 가 done/ backfill 로 교정. vitest 13 files/81(+29, 0 regression)·lint 0·build ✓. closed via lifecycle chore PR #582. 분석=Opus 4.7 / 구현=Opus 4.7 / 리뷰=Opus 4.7.

- `TASK-PC-FE-002a-console-operator-token-exchange-wiring.md` — impl PR #574 (squash → main `aa7282b4`). frontend-engineer(Opus). **ADR-MONO-014 § D5 step 2** — console-side bridge + **#569 `console-integration-contract.md` §2.1↔§2.2 자기모순 fix**. spec-first(§2.1 +server-side exchange step+trust-boundary 불변식 / §2.2 L29 = operator token via exchange, producer 요구 불변 / 신규 normative §2.6 Operator Token Exchange + console-web/architecture.md auth-flow; GAP spec 무변경 cross-ref만). code: `operator-token-exchange.ts`(server-only RFC8693, body admin-api.md verbatim, 401→fail_closed/400·5xx·timeout·network·bad-tokenType→unavailable, 토큰 로깅 0, GAP fallback 절대 無) + `session.ts`(OPERATOR_COOKIE/getOperatorToken, isAuthenticated=GAP+operator) + callback(exchange 후 store; **실패 시 GAP 쿠키까지 삭제** → partial-authed 無, #569 차단) + refresh(re-exchange ADR-014 D2; 실패→전세션 drop 401) + logout(operator 쿠키 clear) + registry-client(`getOperatorToken()` — #569 코드 fix). 3대 보안임계 머지 전 직접검증. ⚠ task md 미커밋 갭(FE-002 동일) — PR #582 가 done/ backfill 교정. vitest 9 files/45·lint 0·build ✓. closed via lifecycle chore PR #582. 분석=Opus 4.7 / 구현=Opus 4.7 / 리뷰=Opus 4.7.

- `TASK-PC-FE-001-console-web-shell-gap-sso.md` — impl PR #569 (squash → main `debc6849`). frontend-engineer(Opus) — ADR-MONO-013 Phase 1→2 bridge. 선행 GAP `TASK-BE-296`(#568). Layered-by-Feature 셸: GAP OIDC Auth Code+PKCE server routes(`app/api/auth/{login,callback,refresh,logout,session}`+`shared/lib/{pkce,session}`) HttpOnly·Secure·SameSite=strict 쿠키·public client·401→server refresh + data-driven 카탈로그(features/catalog, `available:false`→coming-soon, 코드변경 0) + 테넌트 스위처 + resilience + a11y(WCAG AA, axe)+web-vitals+perf budget. 경로 정렬(.env/compose/contract → BE-296 권위 `http://gap.local/api/admin/console/registry`). 오케스트레이터 2-fix(stream-timeout 유실→직접검증: auth-routes.test vi.hoisted TDZ / ServiceTile aria-disabled→div role=group). `pnpm build` 7-route 99.9KB·lint 0·vitest 6/6 suites 26/26. scope=platform-console only. closed via lifecycle chore PR #582. 분석=Opus 4.7 / 구현=Opus 4.7 / 리뷰=Opus 4.7.

## archive

(empty)
