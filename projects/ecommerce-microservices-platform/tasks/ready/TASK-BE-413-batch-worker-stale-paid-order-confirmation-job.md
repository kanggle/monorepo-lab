---
id: TASK-BE-413
title: batch-worker stalePaidOrderConfirmationJob — forward-confirm paid-but-unconfirmed PENDING orders
status: ready
project: ecommerce-microservices-platform
service: batch-worker
type: feature
created: 2026-06-20
---

# TASK-BE-413 — batch-worker `stalePaidOrderConfirmationJob`

> Recommended implementation model: **Sonnet** (Opus acceptable). Pattern is a near-mechanical
> mirror of BE-409 (ShedLock scheduler + client + job + batch-history) plus a caller-side
> `IamClientCredentialsTokenProvider` mirror of product-service BE-402.
> **DEPENDS ON TASK-BE-412 being live** — this job calls the order-service internal endpoint that
> BE-412 implements. Do not start until BE-412 is merged (or coordinate so the endpoint exists in
> the target env before the IT runs against it). Unblocked-decision: TASK-BE-410 (done).

## Goal

Add the `stalePaidOrderConfirmationJob` to batch-worker: every 10 minutes, call order-service's
`POST /api/internal/orders/confirm-paid-stale` (with `client_credentials` Bearer auth) so paid-but-
unconfirmed stale `PENDING` orders are **forward-confirmed**. Record each run in
`batch_job_execution_history`. batch-worker only triggers the sweep; order-service evaluates the
predicate + performs the transition + emits the event server-side.

## Scope

In:
- `StalePaidOrderConfirmationScheduler` — thin scheduling shell, `@Scheduled(cron = "0 */10 * * * *")`
  + `@SchedulerLock(name = "batch-stale-paid-order-confirmation", lockAtMostFor = ..., lockAtLeastFor = ...)`,
  delegating to a `StalePaidOrderConfirmationJob#execute()` (business logic directly callable from
  tests, bypassing ShedLock — same structure as `SearchIndexConsistencyScheduler` from BE-409).
- `OrderServiceClient` (`RestClient`, `@Value` base-url, explicit connect/read timeouts like
  BE-409's `ProductServiceClient`) calling `POST /api/internal/orders/confirm-paid-stale` with the
  request `{ olderThanMinutes, limit }` (config-driven defaults 30 / 200) and parsing the
  `{ scanned, confirmed, skipped, confirmedOrderIds? }` response.
- `IamClientCredentialsTokenProvider` in batch-worker — **mirror product-service's** (plain
  `RestClient` + Jackson, cached token, `REFRESH_SKEW` before expiry, lazy acquisition). Config keys
  `iam.internal-client.{token-uri,client-id,client-secret}` with `client-id = ecommerce-internal-services-client`.
  `OrderServiceClient` stamps `Authorization: Bearer <provider.currentBearer()>` on each call.
- Batch-history recording: a `COMPLETED` row (with the confirmed/skipped tally in a log/structured
  field) on success, a `FAILED` row with the error reason on any client failure — and the failure
  MUST NOT propagate past the job (BE-409 isolation rule: one job's failure never stops the scheduler
  or other jobs).
- Metrics `batch_paid_orders_confirmed_total` + `batch_paid_orders_confirm_skipped_total` (per
  observability.md), plus the standard `batch_job_total` / `batch_job_duration_seconds` /
  `batch_job_failure_total` already emitted by the batch harness.
- Unit tests for the job (mock `OrderServiceClient` → assert history + metrics on success and on a
  thrown client error → FAILED row, no propagation). Integration test wiring the client against a
  stub/WireMock (or the real endpoint if BE-412 is deployed in the IT env) — assert the request shape
  (`olderThanMinutes`, `limit`, Bearer header present) and that a 401 from the endpoint records FAILED
  without crashing the scheduler.

Out:
- The order-service endpoint itself — that is **TASK-BE-412** (prerequisite).
- Any direct DB access into order's store (forbidden — HTTP system-command only).
- IAM client/secret seed (tracked in iam-integration.md; dev `.env` secret; the provider acquires
  lazily and a token-acquisition failure surfaces as a FAILED run, not a startup coupling).

## Acceptance Criteria

- [ ] **AC-1**: `stalePaidOrderConfirmationJob` fires on `@Scheduled(cron = "0 */10 * * * *")` under
  a `batch-stale-paid-order-confirmation` ShedLock so only one replica runs per tick; business logic
  in an `execute()` method directly callable from tests (ShedLock-bypassing), mirroring BE-409.
- [ ] **AC-2**: `OrderServiceClient` calls `POST /api/internal/orders/confirm-paid-stale` with
  `{ olderThanMinutes (default 30), limit (default 200) }` and a `client_credentials` Bearer header
  minted/cached by a batch-worker `IamClientCredentialsTokenProvider` (mirrors product-service BE-402).
- [ ] **AC-3**: Each run records a `batch_job_execution_history` row — `COMPLETED` (with tally) on
  success, `FAILED` (with reason) on any client error; the error NEVER propagates to stop the
  scheduler or sibling jobs.
- [ ] **AC-4**: Idempotency/isolation — re-runs are safe (order-service skips already-confirmed
  orders); a transient order-service failure (incl. 401) yields a FAILED history row and a clean
  next-tick retry.
- [ ] **AC-5**: Metrics `batch_paid_orders_confirmed_total` + `batch_paid_orders_confirm_skipped_total`
  reflect the endpoint's `confirmed`/`skipped` tally per run.
- [ ] **AC-6**: Unit + integration tests cover success, client-failure isolation, and the request/auth
  shape (Bearer header present, correct body).

## Related Specs

- `specs/services/batch-worker/{overview,architecture,dependencies,observability}.md` (job reframed +
  order-service authorized + client_credentials + metrics)
- `specs/contracts/http/internal/order-confirm-paid-stale.md` (the endpoint this job calls)
- `specs/integration/iam-integration.md` (`ecommerce-internal-services-client` ACTIVE; token provider pattern)
- `platform/service-types/batch-job.md` (batch-worker Service Type — ShedLock "분산락 필수", idempotency)
- BE-409 (`TASK-BE-409`, done) — ShedLock scheduler/job/client/batch-history scaffolding to mirror
- BE-402 (product-service `IamClientCredentialsTokenProvider`) — caller-side token provider to mirror

## Related Contracts

- `specs/contracts/http/internal/order-confirm-paid-stale.md` (caller of this contract)

## Edge Cases

- order-service returns `{ scanned:0, ... }` (nothing stale) → COMPLETED, no-op, metrics 0.
- order-service 401 (mis-seeded client / wrong secret) → FAILED history row, scheduler survives, retry next tick.
- order-service timeout / 5xx → FAILED row, isolated; explicit connect/read timeouts so a hung
  endpoint never holds the ShedLock window.
- Two replicas tick simultaneously → ShedLock guarantees one execution.
- Token expiry mid-window → provider refreshes (`REFRESH_SKEW`) on next acquisition.

## Failure Scenarios

- Calling before BE-412 is live → every run 404s/connection-refused → FAILED rows. Gate start on
  BE-412 (prerequisite).
- Direct DB write into order's store instead of the HTTP system-command → service-boundary violation
  (HARDSTOP-03-adjacent / `service-boundaries.md`). HTTP-only.
- Swallowing the failure WITHOUT a FAILED history row → silent recovery gap (operators can't see the
  job is failing). Record FAILED, isolate, do not propagate.
