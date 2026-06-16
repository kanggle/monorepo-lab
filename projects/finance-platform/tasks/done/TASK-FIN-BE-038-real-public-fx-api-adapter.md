# TASK-FIN-BE-038 — real public FX API adapter (mode=real, Frankfurter)

**Status:** done
**Domain:** finance · **Service:** ledger-service · **Type:** backend infrastructure adapter (external HTTP integration)
**Parent:** ADR-002 (realtime FX rate feed, ACCEPTED 2026-06-15) — § 3.1 roadmap item 3 (deferred): *"실제 공개 FX API 어댑터(`mode=<real>`)"*. Builds on FIN-BE-031 (port + noop/stub/http adapters + cache + shadow poller) and FIN-BE-032 (consume + staleness + audit).

> **Task number:** FIN-BE-038 (finance FIN-BE counter; 037 is the current max).

## Goal

Add a **concrete real public FX API adapter** implementing `FxRateProviderPort`, wired by
`financeplatform.ledger.fxrate.mode=real`, that fetches live mid-market rates from **Frankfurter**
(frankfurter.app / api.frankfurter.dev — **no API key**, ECB daily reference rates). The existing
`http` adapter (FIN-BE-031) expects a **bespoke** `{"rate","asOf"}` shape that no real provider
emits; this task adds an adapter that speaks an **actual** public API's response format so the feed
can run against a real source, not just the deterministic `stub`.

## Provider (decided — TASK gate)
**Frankfurter** (no-key, ECB). `GET <baseUrl>/latest?from=<FROM>&to=<TO>` →
`{"amount":1.0,"base":"<FROM>","date":"YYYY-MM-DD","rates":{"<TO>":<number>}}`.
Default base URL `https://api.frankfurter.dev/v1` (configurable). No auth header.

## Scope
`ledger-service` only. Additive, config-gated, **net-zero** (default `mode=noop` unchanged; `real`
only active when explicitly configured). No change to the port, the use-cases, the cache, the
poller, or the staleness/audit logic — only a new adapter bean + its config block. Out of scope
(separate deferred items): `fx_rate_quote_history` table, console FX dashboard / manual refresh,
per-tenant rate override.

### Changes
- **Adapter** — `RealFxRateProviderAdapter implements FxRateProviderPort`,
  `@ConditionalOnProperty(name="financeplatform.ledger.fxrate.mode", havingValue="real")`. Mirror
  `HttpFxRateProviderAdapter`'s structure: build the `RestClient` **once** in the ctor via
  `ResilienceClientFactory.buildRestClient(baseUrl, connectMs, readMs)` (libs/java-common — never
  `new RestTemplate()`); **best-effort, never-throw** (every failure mode → `Optional.empty()`).
- **Rate direction (CRUX — get this exactly right)** — the port's `RateQuote.rate` is
  **base-minor-per-foreign-minor** (KRW per 1 USD for base=KRW, foreign=USD), the same convention as
  `closingRate`/`settlementRate`. `latestQuote(base, foreign)` is called with **base=KRW**
  (`LedgerReportingCurrency.BASE`), foreign=the leg. Frankfurter `from`/`to` semantics:
  `?from=USD&to=KRW` returns `rates.KRW` = **KRW per 1 USD**. Therefore map
  **`from=foreign.code()`, `to=base.code()`, rate = response.rates[base.code()]**. (Do NOT swap —
  `from=base&to=foreign` would invert the rate.)
- **asOf** — parse Frankfurter `date` (a `LocalDate`, ECB daily) → `Instant` at `00:00:00Z`
  (`LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant()`). If `date` is absent/garbage,
  fall back to `clock.now()` (use the existing `ClockPort`, as the http adapter does).
- **source tag** — `"real:frankfurter"` (or `"real:" + host`), persisted on the quote for audit
  (ADR-002 audit-heavy — the applied quote's source must be traceable).
- **Config** — add a `Real` block to `FxRateFeedProperties` (`baseUrl` default
  `https://api.frankfurter.dev/v1`, `connectTimeoutMs` default 2000, `readTimeoutMs` default 5000),
  mirroring the existing `Http` block. Update the `mode` Javadoc to list `noop`/`stub`/`http`/`real`.
- **Fail-soft ctor** — a blank/null base URL wires an inert adapter that always returns empty
  (mirror `HttpFxRateProviderAdapter`'s `configured` guard) — never fail context start.

## Acceptance Criteria
- `mode=real` wires `RealFxRateProviderAdapter`; `mode=noop`/`stub`/`http` are unaffected (the
  `@ConditionalOnProperty` keeps exactly one provider bean active — verify no bean-conflict).
- A MockWebServer IT: enqueue a Frankfurter-shaped body for `from=USD&to=KRW`
  (`{"base":"USD","date":"2026-06-16","rates":{"KRW":1361.23}}`) and assert
  `latestQuote(KRW, USD)` returns `rate=1361.23`, `asOf=2026-06-16T00:00:00Z`,
  `source` starts with `"real:"`. **Assert the request path/query** (`from=USD&to=KRW`) to lock the
  direction mapping.
- Best-effort never-throw IT cases: non-2xx (500), connection error / timeout, missing `rates` key,
  unknown foreign code (rates map lacks `to`), malformed `date` → all `Optional.empty()` (or
  clock-fallback asOf for the date case), never an exception.
- Default net-zero: with no `mode` set (noop), zero external calls (existing behavior unchanged).
- Module unit/IT tests GREEN (`:projects:finance-platform:apps:ledger-service:test`). Testcontainers
  Docker ITs (`@Tag("integration")`) excluded from the default test task — MockWebServer is not
  Testcontainers, so the adapter IT runs in the plain test task.

## Related Specs / Contracts
- `projects/finance-platform/docs/adr/ADR-002-realtime-fx-rate-feed.md` (D1/D5 + § 3.1 item 3 — mark
  the real-adapter roadmap item delivered; keep `history` / console / per-tenant as still-deferred).
- `projects/finance-platform/specs/services/ledger-service/*` — FX feed section if it enumerates
  adapter modes (add `real`).

## Edge Cases
- Foreign code not present in `rates` (provider doesn't quote that pair) → empty (skip leg; partial
  cache load allowed — `RefreshFxRateQuotesUseCase` already tolerates empty).
- Frankfurter occasionally returns `amount`/`base` echoes — ignore; only read `rates[to]`.
- Weekend/holiday: Frankfurter returns the last working day's `date` — that's the correct `asOf`;
  the staleness guard (FIN-BE-032, max-age) is what rejects too-old quotes, NOT this adapter.
- Base==foreign (KRW→KRW) is never polled (pairs are foreign legs only) — no special-case needed.

## Failure Scenarios
- Wrong direction mapping (`from=base&to=foreign`) → inverted rate (e.g. ~0.0007 instead of ~1361) →
  catastrophic P&L mis-valuation. The IT path/query assertion + rate assertion guards this.
- Provider outage/latency: best-effort empty + the operator manual-input path is untouched
  (ADR-002 D3/D4 decoupling) — no blocking of revaluation/settlement.
- Non-net-zero regression: if the new bean activates outside `mode=real`, unconfigured envs would
  make external calls. The `@ConditionalOnProperty havingValue="real"` + default `mode=noop` prevent
  this; the bean-conflict AC verifies exactly-one-active.
