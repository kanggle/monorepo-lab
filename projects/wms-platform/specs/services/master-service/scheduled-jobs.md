# master-service — Scheduled Jobs

Design of master-service's background scheduled jobs. Referenced by
[`specs/contracts/http/master-service-api.md`](../../contracts/http/master-service-api.md)
§ 6.7 (lot expiration is **not** a public endpoint — it is driven by the daily
job below). The domain rule for lot expiry lives in
[`domain-model.md`](domain-model.md) §6.

---

## Job: Lot Expiration (`ACTIVE → EXPIRED`)

Daily batch that transitions every `Lot` whose `expiry_date` has passed from
`ACTIVE` to `EXPIRED`, emitting one `master.lot.expired` event per successful
transition.

### Schedule

- **Cron:** `0 5 0 * * *` — 00:05 server-local time, daily.
- 00:05 (not midnight) avoids the midnight thundering-herd window and any
  DST-flip edge case; `LocalDate.now(Clock)` resolves "today" idempotently
  regardless.
- Timezone: production uses the server's default-zone clock. Per-warehouse /
  per-tenant timezone handling is **deferred to v2** (out of scope for the
  first increment).

### Selection predicate (strict `<`)

- A lot is expired when `expiry_date < today`.
- A lot with `expiry_date == today` does **not** expire until the next day's
  run (strict less-than, mirroring `domain-model.md` §6).
- A lot with **no** `expiry_date` is never expired by the scheduler.

### Transactional behavior

- Each successful transition updates the lot row and appends one
  `master.lot.expired` (`LotExpiredEvent`) to the **outbox in the same
  transaction** — the scheduler never talks to Kafka directly (outbox relay
  publishes). See [`../../contracts/events/`](../../contracts/events/) for the
  event envelope.
- **Idempotent:** re-running the job is safe — already-`EXPIRED` rows no longer
  match `expiry_date < today AND status = ACTIVE`, so a second run on the same
  day transitions nothing.
- **Per-row isolation:** the batch reports `(considered, expired, failed)`; a
  single row's failure does not abort the batch.

### Outcome / observability

- Returns `LotExpirationResult(considered, expired, failed)`; the scheduler
  logs `considered/expired/failed` counts at INFO.
- **Top-level safety net:** the scheduled thread must never die — a batch-level
  `RuntimeException` is caught and reported as `failed=1` (rather than
  propagating and killing the scheduler thread), so metrics/observers see a
  non-zero failed count reflecting the crash.

### Configuration & test seam

- **Property gate:** `wms.scheduler.lot-expiration.enabled`
  (`@ConditionalOnProperty`, `matchIfMissing = true`) — the bean is active by
  default in production.
- `application-integration.yml` sets it to `false` so integration / e2e
  harnesses invoke `runNow()` (or the `ExpireLotsBatchUseCase` directly)
  against a pinned clock instead of waiting for the cron to fire.
- `runNow()` is public for exactly this reason; `runScheduled()` (the
  `@Scheduled` entry point) simply delegates to it.

### Components

- `LotExpirationScheduler` (`scheduler/`) — `@Scheduled` entry point + `runNow()`.
- `ExpireLotsBatchUseCase` (inbound port) — `execute(LocalDate today)` →
  `LotExpirationResult`. The caller passes `today` so the boundary is
  controllable in tests without freezing the global clock.
- `LotExpirationBatchProcessor` (application service) — per-row transition +
  `LotExpiredEvent` outbox append.

### Edge cases

| Case | Behavior |
|---|---|
| `expiry_date == today` | Not expired until tomorrow's run (strict `<`). |
| `expiry_date` null | Never expired by the scheduler. |
| Already `EXPIRED` | Skipped (no longer matches the query) — re-run idempotent. |
| Single-row failure | Counted in `failed`; batch continues. |
| Batch-level crash | Caught, reported as `failed=1`; scheduler thread survives. |

### Out of scope (v2)

- Per-warehouse / per-tenant timezone-aware expiry boundaries.
- A public "expire now" admin endpoint (expiry is schedule-driven only;
  manual `reactivate` exists per master-service-api.md §6.7).
