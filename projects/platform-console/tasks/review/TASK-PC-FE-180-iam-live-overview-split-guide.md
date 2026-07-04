# TASK-PC-FE-180 — split IAM 개요 into a live **overview snapshot** + a dedicated **가이드**

- **Status**: review
- **Type**: TASK-PC-FE (console-web)
- **Depends on**: TASK-PC-FE-163 (the current static IAM guide at `/iam` — this task relocates it), TASK-PC-FE-156 (ecommerce overview-snapshot pattern this mirrors)
- **Analysis model**: Opus 4.8 · **Impl model recommendation**: Sonnet (verified overview-snapshot pattern replication + IAM-specific edges)

## Goal

Today `/iam` renders the **static RBAC guide** (`IamGuideScreen`, PC-FE-163) but is labeled `개요` — inconsistent with every other domain whose `개요` is a **live operational page** (wms/scm/erp/ecommerce). Split the two concerns:

1. **`/iam` → live overview snapshot** — a domain-internal operator overview mirroring the ecommerce `/ecommerce` snapshot (PC-FE-156): live counts + recent activity fanned out over the **existing** IAM list endpoints (operators / accounts / audit). No new producer endpoint.
2. **`/iam/guide` → the existing static guide** — `IamGuideScreen` relocated verbatim (label `가이드`), so the reference material stays a single coherent page.

## Architecture — console-web direct fan-out (chosen; no backend/producer change)

Same model as PC-FE-156 (§2.4.10.6) / the wms·scm·erp overview-snapshot series: counts derived from the existing list endpoints' `totalElements` with `page=0&size=1`; recent rows from `size=5`. The IAM admin surfaces are already console-web → IAM admin-service via the **exchanged operator token** (`getOperatorToken()`, contract §2.4.1/2.4.2/2.4.3), so the fan-out runs **server-side in the `/iam` page** reusing the existing typed api fns:

- `features/operators/api` → `listOperators({ status?, page, size })` → `OperatorPage {content, totalElements}` (status filter: `ACTIVE` | `SUSPENDED`).
- `features/accounts/api` → `searchAccounts({ page, size })` → `AccountPage {content, totalElements}`.
- `features/audit/api` → `queryAudit({ source?, page, size })` → `AuditPage {content, totalElements}`.

**No console-bff leg, no new producer endpoint, no `/summary` aggregation** (ADR-MONO-017 D3.B — same discipline as PC-FE-156). Contrast: §2.4.4 (BFF-composed console-wide operator overview) and §2.4.9.1 are different, BFF-fan-out surfaces — this is a domain-internal snapshot, so the direct model fits.

Data assembled by the fan-out:

- **운영자 카드** — `listOperators({page:0,size:1}).totalElements` (총계) + `ACTIVE` / `SUSPENDED` status-filtered totals (breakdown). Card = `Link` → `/operators`.
- **계정 카드** — `searchAccounts({page:0,size:1}).totalElements` (활성 테넌트 스코프 총계). Card = `Link` → `/accounts`. (No lock/suspended breakdown — the `/api/accounts` search proxy has no status filter; a breakdown would need a producer status param. Out of scope — total only.)
- **감사·보안 카드** — recent 5 events `queryAudit({page:0,size:5}).content` + `totalElements` (스코프 내 총 이벤트). Card = `Link` → `/audit`. Optional source distribution (`queryAudit({source, page:0, size:1})` per `AUDIT_SOURCES`) is a stretch; the security source leg may `403` without `security.event.read` → that cell renders `권한 없음` (never blanks the card).

## Resilience (§2.5) — the decisive rule (IAM-specific error taxonomy)

Fan-out is bounded + parallel (`Promise.all` over per-cell promises). Each cell **catches its own error into a cell state** — but the IAM clients throw a DIFFERENT taxonomy than ecommerce's single `ApiError`:

- `ApiError` **401** → **re-thrown** so the top-level catch performs a whole-session `redirect('/login')` (no partial authed state).
- `ApiError` **403** (`PERMISSION_DENIED` / `TENANT_SCOPE_DENIED`) → cell `forbidden` (`권한 없음`). **Expected & normal** for narrow roles — the [RBAC access matrix](../../apps/console-web/src/features/iam-guide/components/IamGuideScreen.tsx) says e.g. `SUPPORT_LOCK` lacks `account.read`, `SECURITY_ANALYST` lacks `operator.manage`. Do NOT render forbidden as an outage.
- `OperatorsUnavailableError` / `AccountsUnavailableError` / `AuditUnavailableError` (503 / timeout / network) → cell `degraded` (`점검 필요`).
- `ApiError` **400 `NO_ACTIVE_TENANT`** → see Edge Cases: this is a **page-level tenant gate**, not a per-cell degrade (all three legs require an active tenant).

One card's degrade/forbidden never blanks the section; the `(console)` shell + other cards stay intact.

## Scope

Under `projects/platform-console/apps/console-web/src/`:

- **NEW `features/iam-overview/api/overview-state.ts`** — `getIamOverviewState()` → `IamOverviewState` (operators {total, active, suspended, status}, accounts {total, status}, audit {total, recent[], status}), each leg carrying `status: 'ok'|'forbidden'|'degraded'`. Reuses the three feature api fns. A 401 in any leg re-throws → redirect. Bounded: `size` 1 (counts) / 5 (recent); no auto-refetch. A `NO_ACTIVE_TENANT` short-circuit → `{ noActiveTenant: true }`.
- **NEW `features/iam-overview/components/IamOverviewScreen.tsx`** — presentational **server component** (no `'use client'`): three count/summary cards (each the `Link` to its IAM screen), the operator ACTIVE/SUSPENDED split, the recent-audit mini-list. Degraded/forbidden cells render a compact `점검 필요`/`권한 없음` placeholder instead of a number (never blanks). A `noActiveTenant` state renders the tenant-gate note (mirror the sibling IAM screens).
- **NEW `features/iam-overview/index.ts`** — barrel.
- **NEW `features/iam-overview/**/*.test.tsx`** — `overview-state` (count mapping, 401 redirect, per-cell degrade/forbidden, no-active-tenant short-circuit) + `IamOverviewScreen` (renders counts / degraded cells / tenant gate).
- **REWRITE `app/(console)/iam/page.tsx`** — now the live overview: `export const dynamic = 'force-dynamic'` (data fetch), call `getIamOverviewState()`, render `<IamOverviewScreen />`. A 401 → `redirect('/login')`.
- **NEW `app/(console)/iam/guide/page.tsx`** — renders the relocated `<IamGuideScreen />` (static; no `force-dynamic`). `IamGuideScreen` itself moves unchanged; only its `<h1>` text `IAM 개요` → `IAM 가이드`.
- **MODIFY `shared/ui/ConsoleSidebarNav.tsx`** — IAM drill children become: `개요`(`/iam`, testid `nav-iam-overview` **NEW**) → `가이드`(`/iam/guide`, testid `nav-iam-guide` **MOVED here**) → `운영자 관리` → `계정 운영` → `감사·보안`. (`개요` stays the first child — orient before operating.)

Spec:

- **`specs/contracts/console-integration-contract.md`** — add **§2.4.3.1 IAM domain overview snapshot** (follows the 2.4.1/2.4.2/2.4.3 IAM surfaces; mirror the §2.4.10.6 ecommerce snapshot text): console-web direct fan-out, reuses **existing** operators/accounts/audit list endpoints via `size=1` count + `size=5` recent, ADR-MONO-017 D3.B (no `/summary`), 401→login / per-cell degrade/forbidden / page-level `NO_ACTIVE_TENANT` gate. Explicitly contrast with §2.4.4 (BFF-composed) & §2.4.9.1. **No new consumed producer endpoint.**
- **`specs/services/console-web/architecture.md`** — update the IAM section line: `/iam` = live overview snapshot (`iam-overview` feature) + `/iam/guide` = static guide (relocated `iam-guide`).

## Non-Goals

- No producer change, no console-bff change, no new `/summary` endpoint, no mutation, no auto-refresh/polling.
- No account lock/suspended breakdown (producer search has no status filter — total only).
- No per-screen menu pre-hide (producer-authority principle unchanged — cells surface `권한 없음` inline, never hide).
- No change to `IamGuideScreen`'s content/logic beyond the `<h1>` text + its route location.

## Acceptance Criteria

- **AC-1** `/iam` renders a live overview: 운영자 카드 (총계 + ACTIVE/SUSPENDED split), 계정 카드 (총계), 감사·보안 카드 (총계 + 최근 5건). Each count card is a `Link` to its IAM screen (`/operators` · `/accounts` · `/audit`).
- **AC-2** Counts come from `list*({page:0,size:1}).totalElements`; recent audit from `size:5`; operator split from `status`-filtered lists. NO new producer endpoint / no `/summary` / no console-bff call.
- **AC-3** Per-cell resilience: `403` cell → `권한 없음`; `503`/timeout/network cell → `점검 필요` (compact, no crash, other cells unaffected); `401` from ANY leg → `redirect('/login')`.
- **AC-4** `NO_ACTIVE_TENANT` (no tenant selected) → the overview renders a page-level tenant-gate note (no three duplicate per-cell messages), mirroring the sibling IAM screens; shell + other sections intact.
- **AC-5** `/iam/guide` renders the full former guide (roles / access matrix / delegation / onboarding axes / domain roles / permission keys) with heading `IAM 가이드`. IAM sidebar drill shows `개요`(→`/iam`) first, then `가이드`(→`/iam/guide`).
- **AC-6** testid migration is consistent: `nav-iam-guide` now targets `/iam/guide`; `/iam` (개요) gets `nav-iam-overview`. Any E2E/unit selector asserting `nav-iam-guide → /iam` is updated to `/iam/guide`.
- **AC-7** Contract §2.4.3.1 added + architecture IAM line updated; ADR-MONO-017 D3.B compliance noted.
- **AC-8** `pnpm lint` + `tsc --noEmit` + `vitest` (targeted) green — new `iam-overview` tests + updated nav/guide tests.

## Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` §2.4.1/2.4.2/2.4.3 (IAM surfaces — reused endpoints), §2.4.10.6 (ecommerce overview-snapshot reference pattern), §2.4.4 / §2.4.9.1 (BFF-composed overview — the contrast), §2.5 (resilience).
- `projects/platform-console/specs/services/console-web/architecture.md` (Server vs Client Components; IAM section).
- `projects/iam-platform/specs/services/admin-service/rbac.md` (Seed Matrix — why per-cell `403` is normal for narrow roles).

## Related Contracts

- Producer: IAM admin-service operators/accounts/audit **list** endpoints (`GET /api/admin/{operators,accounts,audit}?page&size&status&source`). Consumed read-only, already documented (§2.4.1/2/3); **no redefinition**.

## Edge Cases

- **No active tenant** — all three legs throw `400 NO_ACTIVE_TENANT` before fetch. Short-circuit to a single page-level tenant-gate note (not three per-cell messages).
- **Narrow-role operator** (e.g. `SUPPORT_LOCK`, `SECURITY_ANALYST`) — some legs `403` by design; those cards render `권한 없음` while the permitted cards render live counts. This is correct, not an outage.
- **SUPER_ADMIN with `*` active tenant** — legs inherit the sibling-screen behavior (producer handles `*` cross-tenant); no new risk introduced by the overview.
- **Empty scope (all counts 0)** — cards render `0` (valid), not degraded.
- **Recent audit rows carry masked PII** — render as text only; never logged (inherits `queryAudit` redaction). No navigation double-encode on the overview itself.
- **Security source leg without `security.event.read`** — the optional source-distribution `security` bucket `403` → `권한 없음` for that bucket only; the audit card total + recent list (basic `audit.read`) still render.

## Failure Scenarios

- All legs 503 → every card `점검 필요`, but the section shell + guide link render (degrade-safe, §2.5).
- `401` mid-fan-out (session expired) → `redirect('/login')`; no half-rendered authed snapshot.
- Count leg ok but recent-audit leg 503 → operator/account counts render; audit card shows total-degraded/recent-`점검 필요` compactly.
- testid drift — an un-updated `nav-iam-guide → /iam` selector would fail; AC-6 + the updated nav test catch it.
