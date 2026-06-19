---
id: TASK-MONO-300
title: "FX rate manual refresh — finance POST endpoint + console-web refresh action (ADR-002 deferred)"
status: ready
scope: cross-project
projects: [finance-platform, platform-console]
tags: [code, test, fx, console, cross-project]
analysis_model: "Opus 4.8"
impl_model: "Sonnet 4.6"
created: 2026-06-19
---

# TASK-MONO-300 — FX rate manual refresh

## Goal

Realize the last remaining ADR-002 (FX rate feed) deferred item: the **manual refresh** action on
the console FX-rate surface. The **dashboard READ already exists** (`GET /api/finance/ledger/fx-rates`
+ console-web `FxRatesTable` in the ledger-ops `fx-rates` tab, PC-FE-092/104) — this task adds ONLY
the operator-triggered **on-demand refresh** (finance endpoint + console-web action), completing the
"환율 대시보드 + 수동 refresh" item. Atomic cross-project (finance + platform-console).

## Current state (verified)

- finance `FxRateController` (`projects/finance-platform/apps/ledger-service/.../presentation/controller/FxRateController.java`)
  has `GET /api/finance/ledger/fx-rates` (list all current quotes → `FxRatesResponse{feedEnabled, rates[]}`)
  + `GET .../{foreignCurrency}/history`. Operator-auth via `/api/finance/**` rule +
  `ActorContextResolver.currentOrThrow()`. NO refresh endpoint yet.
- `RefreshFxRateQuotesUseCase.refresh()` exists (the ShedLock-guarded scheduled poller invokes it;
  polls all configured pairs via `FxRateProviderPort`, upserts `fx_rate_quote`, appends history;
  per-pair isolation, best-effort, idempotent last-write-wins; returns count upserted).
- console-web ledger-ops: `features/ledger-ops/api/ledger-reads-api.ts` (`getFxRates()`),
  `api/ledger-client.ts` (`callLedger()` core, domain-facing IAM OIDC token, NO X-Tenant-Id),
  `app/api/ledger/fx-rates/route.ts` (GET proxy), `components/FxRatesTable.tsx` (read table, already
  has an `onRefresh?` prop seam). console-bff is NOT involved (ledger is a direct finance call).

## Scope

### A. finance ledger-service — manual refresh endpoint
1. Add `POST /api/finance/ledger/fx-rates/refresh` to `FxRateController` → invokes
   `RefreshFxRateQuotesUseCase.refresh()` → returns a response DTO, e.g.
   `FxRatesRefreshResponse{ feedEnabled: boolean, refreshed: int }` (count of pairs upserted).
   Operator-auth via the existing `/api/finance/**` rule (`ActorContextResolver.currentOrThrow()`).
2. **Graceful when feed disabled**: if `financeplatform.ledger.fxrate.enabled=false` (noop provider),
   the refresh is a safe no-op → return **200** `{feedEnabled:false, refreshed:0}` (consistent with
   the GET returning `feedEnabled:false, rates:[]`), NOT an error. The use case is already
   best-effort/never-throw — a provider failure must not 500 the endpoint (map to a sane result or a
   422/503 per the existing finance error taxonomy — prefer a 200 with refreshed count when the use
   case completes, even if some pairs failed; the use case logs per-pair failures).
3. Concurrency: a manual refresh + the scheduled poller both call the same idempotent use case
   (last-write-wins upsert) — safe without a new lock. (Do NOT add a ShedLock to the manual path; it
   is a deliberate on-demand operator action.)
4. Tests: controller slice — POST refresh invokes the use case + returns the count; auth required
   (401/403 without operator context); feed-disabled returns 200 no-op.
5. Spec: note the new endpoint in the ledger-service architecture FX section; ADR-002 §3 roadmap —
   mark the console "수동 refresh" deferred item realized (TASK-MONO-300); the FX-feed console surface
   (dashboard + refresh) is now complete.

### B. console-web (ledger-ops) — refresh action
6. New same-origin proxy route `app/api/ledger/fx-rates/refresh/route.ts` (**POST**) → server API
   `refreshFxRates()`. Mirror the existing GET `app/api/ledger/fx-rates/route.ts` + `mapLedgerError`.
7. New server API `refreshFxRates()` in `features/ledger-ops/api/` (reads-api or a mutations module
   matching the existing `resolveDiscrepancy()` mutation pattern in `ledger-reconciliation-api.ts`)
   → `callLedger()` with **POST** to `/api/finance/ledger/fx-rates/refresh`; parse the result with a
   `FxRatesRefreshResponseSchema` (add to `api/types.ts`).
8. Wire the FX rates tab's **refresh action**: `FxRatesTable` already exposes an `onRefresh?` prop —
   add a **Refresh button** (loading/disabled state while in-flight; on success re-fetch the
   `getFxRates` list so the table + staleness update; on error surface via the existing error
   handling). Follow the container/presentational + mutation-button conventions already used in
   ledger-ops (e.g. the reconciliation `resolveDiscrepancy` action button).
9. **F5 invariant**: `rate` stays a string end-to-end (never floated) — unchanged by this task.

## Acceptance Criteria

- AC-1: `POST /api/finance/ledger/fx-rates/refresh` invokes `RefreshFxRateQuotesUseCase.refresh()`,
  returns `{feedEnabled, refreshed}`, operator-auth enforced (401/403 without operator context).
- AC-2: feed-disabled → 200 no-op (`feedEnabled:false, refreshed:0`); a provider/per-pair failure
  does not 500 the endpoint (best-effort).
- AC-3: console-web FX rates tab has a working **Refresh** button — POST then re-fetch the list;
  loading/disabled + error handled.
- AC-4: builds + tests GREEN — `./gradlew :projects:finance-platform:apps:ledger-service:test`
  AND console-web **`pnpm lint` + `npx tsc --noEmit` + `npx vitest run`** (all THREE — `next lint`
  catches unused-import/no-unused-vars that tsc/vitest miss → CI RED otherwise).
- AC-5: e2e-smoke unaffected (this adds a button + a POST route, no redirect/guard/nav-URL change) —
  confirm no `e2e-smoke/*.spec` URL assertion is impacted.
- AC-6: spec updated (FxRateController architecture + ADR-002 roadmap "수동 refresh" realized — the
  FX-feed console surface complete); F5 string invariant preserved (no float).

## Related Specs / Contracts

- `projects/finance-platform/docs/adr/ADR-002-realtime-fx-rate-feed.md` (§3 roadmap — the console
  dashboard+refresh deferred item)
- `projects/finance-platform/apps/ledger-service/.../presentation/controller/FxRateController.java`
  + `application/RefreshFxRateQuotesUseCase.java` + `presentation/dto/FxRatesResponse.java`
- console-web `features/ledger-ops/` (api/ledger-client.ts, ledger-reads-api.ts,
  ledger-reconciliation-api.ts [mutation pattern], components/FxRatesTable.tsx, api/types.ts) +
  `app/api/ledger/fx-rates/route.ts` (GET proxy to mirror)
- memory discipline: console-web worktree needs node_modules (junction from main checkout); push
  requires `pnpm lint` + tsc + vitest (env_console_web_local_verify_needs_lint).

## Edge Cases

- **Feed disabled** (`fxrate.enabled=false`, the default/standalone): refresh is a 200 no-op
  (`feedEnabled:false, refreshed:0`); the button still works but reports nothing refreshed — surface
  this honestly (e.g. a toast "FX feed disabled").
- **External provider transient failure**: the use case is best-effort (per-pair log + continue);
  the endpoint returns the count that succeeded; the console re-fetch shows whatever updated.
- **Double-click / concurrent operators**: idempotent upserts make concurrent refreshes safe; the
  button should disable while in-flight to avoid spamming the external provider.

## Failure Scenarios

- **Refresh 500s on provider failure**: the use case must stay never-throw; the endpoint maps any
  failure to a sane result/4xx, not a 500. Test the feed-disabled + a failing-provider path.
- **console-web push RED from lint**: `next build`/`next lint` flags unused imports that tsc+vitest
  miss → run `pnpm lint` before claiming done (AC-4).
- **F5 float regression**: rate must stay string — no `Number()`/`parseFloat` on the rate path.
- **e2e-smoke RED**: only if a URL/redirect changed (it doesn't here) — confirm.
