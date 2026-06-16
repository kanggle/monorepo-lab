# TASK-FIN-BE-040 — FX rate quote history read endpoint (per-pair drill)

**Status:** done
**Domain:** finance · **Service:** ledger-service · **Type:** backend read endpoint (read-only)
**Parent:** ADR-002 (realtime FX rate feed) — § 3.1 deferred "history read / per-pair drill" (explicitly deferred in FIN-BE-033). Builds on FIN-BE-039 (`fx_rate_quote_history` append-only table) + FIN-BE-033 (the latest-only `fx-rates` read EP). Enables the deferred console FX history drill (a separate PC-FE task will consume this).

> **Task number:** FIN-BE-040 (finance FIN-BE counter; 039 is the current max).

## Goal

Expose the **per-pair FX rate history** (the `fx_rate_quote_history` append-only trail from FIN-BE-039)
as a read endpoint, so an operator can drill into how a currency pair's auto-applied market rate
moved over time (provenance audit). FIN-BE-033 exposes only the **latest** quote per pair; this adds
the **time series** for one pair.

## Scope
`ledger-service` only, **read-only / net-zero**: a new GET endpoint + a repository query + a use-case
+ a response DTO. No write path, no migration (the table exists from V13/FIN-BE-039), no change to the
poller / latest-cache / operator paths. Out of scope (still deferred): the console FX dashboard tab
(separate PC-FE task), manual refresh trigger, per-tenant override, retention/pruning.

### Changes
- **Endpoint** — add to `FxRateController` (base path `/api/finance/ledger/fx-rates`):
  `GET /api/finance/ledger/fx-rates/{foreignCurrency}/history?limit=N`. Base currency is the fixed
  reporting currency KRW (`LedgerReportingCurrency.BASE`) — only the foreign leg is a path variable
  (mirrors the poller's pairs-are-foreign-legs model). Auth: `ActorContextResolver.currentOrThrow()`
  (same as the existing `list()`); tenant-agnostic (market rates are global). Wrap in `ApiEnvelope`.
- **Repository** — add `findHistory(Currency base, Currency foreign, int limit)` to
  `FxRateQuoteHistoryRepository` (+ JPA impl): rows for the pair, ordered `fetched_at` **DESC**
  (newest first), capped to `limit`. Keep the existing `append`/`findAll`.
- **Use-case** — `GetFxRateHistoryUseCase` (`@Transactional(readOnly = true)`): validate/normalize
  the limit (default 50, cap 500, floor 1), parse the foreign code, query, map to a view list
  (`rate` exact-decimal-as-string per the F5 wire convention used by `FxRatesResponse`; `asOf` +
  `fetchedAt` ISO-8601; `source`). Optionally include `ageSeconds`/`stale` per row like FIN-BE-033 —
  OK to omit for history (the staleness flag is a *latest*-quote concept; history is raw provenance).
  Keep it minimal: rate/asOf/fetchedAt/source per row.
- **DTO** — `FxRateHistoryResponse` (base, foreign, `quotes: [{rate, asOf, fetchedAt, source}]`),
  mirroring `FxRatesResponse`'s style (string rate, ISO timestamps).
- **Contract** — `specs/contracts/http/ledger-api.md`: document the new endpoint (path, params,
  `limit` default/cap, 200 with empty `quotes` for an unknown/never-polled pair — NOT 404, mirroring
  the list EP's empty-200 stance, AC). Reconcile the "history read deferred" note.

## Acceptance Criteria
- `GET /api/finance/ledger/fx-rates/USD/history` returns the KRW→USD history rows newest-first; with
  `?limit=N` at most N rows.
- An unknown / never-polled foreign code → **200** with `quotes: []` (not 404), consistent with the
  list EP's empty-cache stance.
- `limit` out of range: ≤0 → floored to 1 (or rejected 400 — pick one and document; prefer floor to
  default-ish min for read robustness), > cap → capped to 500. Document the chosen behavior.
- Unauthenticated → 401/403 (same `ActorContextResolver` enforcement as `list()`).
- Order is strictly `fetched_at` DESC (newest first); ties broken by surrogate `id` DESC for
  determinism.
- Unit test (Docker-free, fake repo): newest-first ordering, limit cap, empty→empty, code parse.
- IT (Testcontainers, Docker-gated → CI Linux): seed two history rows for a pair via two poller runs
  (or direct repo append), GET returns 2 newest-first; `?limit=1` returns the newest only; unknown
  pair → empty.

## Related Specs / Contracts
- `specs/contracts/http/ledger-api.md` (fx-rates section — add the `/{foreignCurrency}/history` EP).
- `specs/services/ledger-service/architecture.md` (FX read surface — note the history drill EP).
- `docs/adr/ADR-002-realtime-fx-rate-feed.md` (§ 3.1 — mark history-read delivered; console dashboard
  + per-tenant + manual-refresh stay deferred).

## Edge Cases
- Pair with one row → single-element list. Pair never polled → empty list (200).
- Very large history (high cadence): the `limit` cap (500) + the `(base,foreign,fetched_at)` index
  from V13 keep the query bounded; no full-table scan.
- `base` is always KRW in v1 — no base path variable; a future multi-base would extend the path.

## Failure Scenarios
- Missing ordering → non-deterministic drill display; the DESC(fetched_at, id) order + the test guard
  prevent it.
- Unbounded query (no limit cap) → unbounded payload on a long-lived high-cadence pair; the cap
  guards it.
- 404 on empty (instead of empty-200) would diverge from the FIN-BE-033 list EP stance — AC pins
  empty-200.
