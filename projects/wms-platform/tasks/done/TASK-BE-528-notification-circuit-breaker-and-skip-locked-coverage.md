# TASK-BE-528 — notification-service: circuit-breaker transitions + SKIP-LOCKED delivery-claim coverage

- **Type**: TASK-BE (test-coverage hardening — no production behavior change)
- **Status**: done
- **Service**: notification-service (wms-platform)
- **Domain/traits**: wms / [event-driven, transactional, multi-tenant]
- **Analysis model**: Opus 4.8 · **Impl model**: Opus (Resilience4j state-machine semantics + real-DB `FOR UPDATE SKIP LOCKED` concurrency / double-claim proof)

## Goal

Close the two CRITICAL correctness surfaces the 2026-07-19 wms audit + orchestrator recon verified as ZERO-covered in notification-service — **no production behavior change** (`src/test` only).

**Verified (recon, 2026-07-19):**

1. **Circuit breaker — zero transition coverage.** `SlackChannelAdapter.send(...)` carries `@CircuitBreaker(name="slack")` + `@Retry(name="slack")` (`adapter/outbound/slack/SlackChannelAdapter.java:71-72`); config in `application.yml:89-110` (`slidingWindowType: TIME_BASED`, size 10, `minimumNumberOfCalls: 5`, `failureRateThreshold: 50`, `waitDurationInOpenState: 10s`, `permittedNumberOfCallsInHalfOpenState: 3`; retry maxAttempts 3, `ChannelPermanentFailureException` ignored). **No test exercises OPEN/HALF-OPEN/CLOSED transitions** (`grep CircuitBreaker|OPEN|HALF_OPEN|CircuitBreakerRegistry src/test` = 0). The only adapter test (`SlackChannelAdapterWireMockTest`, 5 tests) does `new SlackChannelAdapter(...)` directly → **bypasses the AOP proxy → the breaker/retry annotations are never active**; its javadoc claim that transitions are "verified in @SpringBootTest suites" is **false — no such suite exists**.
2. **`FOR UPDATE SKIP LOCKED` delivery-claim query — zero real-DB / concurrency coverage.** `NotificationDeliveryJpaRepository.findPendingDueForRetry` (lines 27-35) uses `@Lock(PESSIMISTIC_WRITE)` + `jakarta.persistence.lock.timeout=-2` (SKIP LOCKED), wired via `DeliveryRepositoryImpl.findAndLockPendingDueForRetry` → `DeliveryExecutor.dispatchDueRetries` → `DeliveryRetryScheduler.poll` (`@Scheduled`). **`DeliveryRepositoryImpl` has no test file and no IT**; the only `findAndLockPendingDueForRetry` in tests is the in-memory fake (plain list scan, no locking). **No two-worker double-claim test exists** (`grep ExecutorService|CountDownLatch|concurren` in delivery tests = 0). The exclusivity guarantee is asserted only by code comments, never at runtime.

## Scope

- **In scope** (notification-service `src/test` only — NEW test code, no `src/main` change):
  - **AC-1 Circuit-breaker transition test.** Prove the breaker actually transitions with the configured thresholds. Preferred: a Spring-context test (`@SpringBootTest`) where the `@CircuitBreaker` AOP proxy is active, driving `SlackChannelPort` through the real proxied `SlackChannelAdapter` against a WireMock Slack endpoint — feed enough failures (≥`minimumNumberOfCalls`, >`failureRateThreshold`) to force CLOSED→OPEN (assert calls now short-circuit without hitting WireMock / throw `CallNotPermittedException`), then after `waitDurationInOpenState` (override to a small value via a test profile) assert HALF-OPEN → CLOSED on success (or → OPEN on continued failure). If full Spring context is disproportionate, drive a `CircuitBreakerRegistry`-created breaker around the adapter call and assert `State` transitions + `CallNotPermittedException` explicitly. Use a test profile to shrink `waitDurationInOpenState` so the test stays fast and deterministic (no real 10s sleeps; prefer Awaitility or a mutable clock/registry over `Thread.sleep`).
  - **AC-2 SKIP-LOCKED delivery IT + double-claim proof.** A `DeliveryRepositoryImpl` Testcontainers IT (extend `NotificationServiceIntegrationBase` — shared static Postgres+Kafka) that: seeds PENDING deliveries, and with TWO concurrent transactions/threads both calling `findAndLockPendingDueForRetry`, asserts **no row is claimed by both** (each PENDING row goes to exactly one worker; the second worker skips locked rows rather than blocking or double-claiming). Also cover the plain claim semantics (only PENDING + due `scheduledRetryAt` returned, ordered by `createdAt`).
  - **AC-3 (optional, if cheap) DeliveryRetryScheduler** — a light test that `poll()` delegates to `dispatchDueRetries` (the `@Scheduled` wrapper is currently zero-covered).
- **Out of scope** (do NOT fix here — separate ticket):
  - The **outbox→Kafka topic-filter defect** (`OutboxPollingScheduler.publishOne` forwards BOTH `notification.delivery.scheduled` and `notification.delivered` to the single topic with no event-type filter, while the contract says `.scheduled` must NOT reach Kafka). Recon's static read strengthens **TASK-BE-524**'s hypothesis, but BE-524 owns the runtime confirmation + the fix (a behavior change). Do NOT add a test that asserts the corrected contract here (it would encode a not-yet-decided fix); do NOT change the poller. If you add any OutboxPollingScheduler coverage, assert only CURRENT behavior and leave a comment pointing to BE-524.
  - Any `src/main` change; master-service (TASK-BE-527); inbound (concurrent sessions).

## Acceptance Criteria

- **AC-1**: A test proves the `slack` breaker transitions CLOSED→OPEN under the configured failure threshold (calls short-circuit with `CallNotPermittedException` once OPEN) and OPEN→HALF-OPEN→CLOSED recovery — through the ACTIVE AOP proxy or a real `CircuitBreaker`, not a bypassed `new SlackChannelAdapter(...)`. Deterministic and fast (no real 10s waits).
- **AC-2**: A Testcontainers IT proves `findAndLockPendingDueForRetry` gives SKIP-LOCKED exclusivity — two concurrent claimants never double-claim a PENDING row — against real Postgres.
- **AC-3**: `:notification-service:test` GREEN and `:notification-service:integrationTest` (Testcontainers lane — authoritative) GREEN. No `src/main` diff (`git diff --stat -- '*/notification-service/src/main'` empty).
- **AC-4 (mutation)**: For AC-2, confirm the test is meaningful — e.g. temporarily drop `SKIP LOCKED`/`PESSIMISTIC_WRITE` (in a scratch copy or by pointing the IT at a query without the lock hint) and confirm the double-claim assertion goes RED; restore. For AC-1, confirm removing the `@CircuitBreaker` (or forcing the breaker permanently CLOSED) makes the OPEN-state assertion RED; restore to byte-identical `src/main`.

## Edge Cases / Failure Scenarios

- The breaker is `TIME_BASED` with `minimumNumberOfCalls: 5` — the test must issue enough calls in-window to satisfy the minimum before the failure-rate is evaluated, or the breaker never leaves CLOSED (a common false-negative). Verify the exact threshold arithmetic against `application.yml`.
- `ChannelPermanentFailureException` (4xx) is in `ignoreExceptions` → it must NOT count toward the failure rate; a transition test must fail with a *retryable* error (5xx/timeout), not a 4xx, or OPEN never triggers.
- SKIP LOCKED requires TWO real DB connections holding transactions simultaneously — a single-threaded test cannot prove it. Use two threads each in its own transaction (e.g. `@Transactional` won't span threads; drive via `TransactionTemplate`/programmatic tx on an `ExecutorService`), with a latch so both hold their locks concurrently. This is the crux — a naive sequential test proves nothing.
- Keep the concurrency IT deterministic (latches, not sleeps) to avoid a flaky lane.
- Testcontainers lane authoritative (local Windows Docker flaky) — CI is the final arbiter.

## Related

- Breaker: `adapter/outbound/slack/SlackChannelAdapter.java:71-72` + `application.yml:89-110`; existing (proxy-bypassing) `SlackChannelAdapterWireMockTest`.
- SKIP LOCKED: `adapter/outbound/persistence/jpa/delivery/NotificationDeliveryJpaRepository.java:27-35`, `DeliveryRepositoryImpl.findAndLockPendingDueForRetry`, `DeliveryExecutor.dispatchDueRetries`, `DeliveryRetryScheduler.poll`; base `integration/NotificationServiceIntegrationBase.java` (shared static Postgres+Kafka).
- Related ticket (do NOT implement here): `TASK-BE-524` (outbox topic-filter — recon strengthened its hypothesis; runtime confirmation + fix is BE-524's).
- Memory: `env_fail_closed_outage_impersonates_security_defect` (breaker/fail-closed asserts must pin the property at the right layer), `env_test_fixture_impossible_input_proves_nothing` (single-thread SKIP-LOCKED proves nothing — need two concurrent tx), `project_testcontainers_docker_desktop_blocker` (CI notification lane authoritative), `platform/testing-strategy.md`.
