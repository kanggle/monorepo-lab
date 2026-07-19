# TASK-BE-529 — notification-service: CI-robust SKIP-LOCKED exclusivity IT (two-claimant no-double-claim)

- **Type**: TASK-BE (test-coverage — deferred from TASK-BE-528 AC-2)
- **Status**: ready
- **Service**: notification-service (wms-platform)
- **Domain/traits**: wms / [event-driven, transactional, multi-tenant]
- **Analysis model**: Opus 4.8 · **Impl model**: Opus (concurrency + CI-lane behaviour)

## Goal

Ship a **CI-robust** notification delivery-claim integration test that BE-528 could not: it covers BOTH
(a) the *plain* claim semantics — `findPendingDueForRetry` returns only PENDING + due rows ordered by
`created_at`, exercising the REAL repository query against real Postgres — AND (b) the SKIP-LOCKED
**exclusivity** guarantee: two retry-scheduler workers claiming concurrently via
`NotificationDeliveryJpaRepository.findPendingDueForRetry`
(`@Lock(PESSIMISTIC_WRITE)` + `jakarta.persistence.lock.timeout = -2`) must **never double-claim the same
PENDING row** (architecture.md § Concurrency Control).

BE-528 shipped the circuit-breaker transition coverage + `DeliveryRetrySchedulerTest` (both in the fast
`test` lane, CI-GREEN) but **the whole `DeliverySkipLockedClaimIntegrationTest` was pulled**: even the simple
single-tx `claimReturnsOnlyPendingDueOrdered` could not be kept because the bundled notification
`integrationTest` lane hung 28 min / cancelled on CI once any new IT was added to it — an
`env_wms_notification_seed_cluster_ci_flake`-class resource/lane issue (see below). So this ticket owns the
entire notification delivery-claim IT, not just the concurrency half.

## Why this is its own ticket (BE-528 history — read before retrying)

Three in-process concurrency shapes were tried in BE-528; **each passed locally (Windows Docker) but failed
on the bundled CI integration lane** (`master + notification + outbound` run together, `--no-parallel`,
30-min job timeout):

1. **Two threads + `CountDownLatch`** (both hold locks, then assert disjoint): CI job **hung 30 min** — a
   thread stuck in a native JDBC lock-wait ignores `shutdownNow()`, leaking an open transaction that blocked
   the `@AfterEach TRUNCATE` until the job timeout; scheduling races also gave uneven splits.
2. **Raw locker connection + real repo claim on a bounded daemon `Future.get(10s)`**: the daemon/Spring-tx/
   `EntityManager` entanglement **poisoned a pooled connection** — the sibling `claimReturns` test's seed
   `INSERT` then failed with a connection I/O error on CI (passed locally).
3. **Two dedicated raw JDBC connections, native `FOR UPDATE SKIP LOCKED`, sequential**: passed locally in
   ~2 min but **hung 28 min on CI** (connection 2 apparently blocked on connection 1's locks — no
   `lock_timeout` on the raw connections, and `c1` only releases in a `finally` after `c2` returns → deadlock).
   Root cause of the local↔CI divergence was never reproduced locally.

4. **Even the trimmed single-tx test** (`claimReturnsOnlyPendingDueOrdered` alone, no concurrency at all) left
   in the class **still hung the bundled `notification:integrationTest` lane 28 min → cancel** on CI, while
   the same commit's `Build & Test` (unit) lane stayed green and BE-527's bundle (same lane, no notification
   IT change) was SUCCESS. So the trigger is **adding *any* IT to notification's bundled lane**, not the test
   body — a strong `env_wms_notification_seed_cluster_ci_flake` (IT-lane resource exhaustion → connection
   severance) signal. **First diagnostic for this ticket: reproduce/confirm the lane behaviour before writing
   the assertion** — the problem is the lane, not (only) the test.

Net lesson (memory `env_ci_flake_is_a_hypothesis_not_a_verdict` + `project_testcontainers_docker_desktop_blocker`
+ `env_wms_notification_seed_cluster_ci_flake`):
**local GREEN is not authoritative for a lock-contention / IT-lane-resource test; the CI lane is** — and a
concurrency IT that can block MUST bound every wait so a failure is fast, not a 30-min hang. Strongly consider
giving notification-service its **own** integration job (unbundled) and/or `--no-parallel` within the lane
before adding IT weight.

## Scope / Acceptance Criteria

- **AC-1**: An IT proves two concurrent claimants never double-claim a PENDING row, and it **cannot hang**:
  every lock wait is bounded (a `SET lock_timeout` issued directly on the JDBC connections the test controls —
  verified to actually take effect on the CI runner, not merely locally — and/or a bounded `Future.get`), so a
  regression (or a non-skip-locked query) fails **fast and loud**, never at the 30-min job timeout.
- **AC-2 (mutation)**: removing the SKIP-LOCKED hint (or pointing at a plain `FOR UPDATE`) makes the test go
  **RED within seconds on CI** — verify on the runner, not only locally.
- **AC-3**: no leaked locks / poisoned pool — a sibling simple test (e.g. a fresh seed+claim) in the same class
  still passes on CI after the concurrency test runs.
- **AC-4**: `:notification-service:integrationTest` GREEN on the **CI** Testcontainers lane (authoritative);
  no `src/main` change.

## Recommended approach (not yet proven — the crux is CI, not code)

- Prefer **two dedicated JDBC connections** (deterministic, single-threaded) BUT set `SET lock_timeout = '5s'`
  on **each raw connection via a `Statement`** immediately after `setAutoCommit(false)` — a direct-connection
  SET is not subject to the Hibernate/`EntityManager` `SET LOCAL` unreliability seen in BE-528 attempt #2.
  Then even if connection 2 blocks, it aborts in 5s (RED), and connection 1's `finally` rollback runs.
- Consider whether the bundled CI job's Postgres/pool differs from local (pool size, `idle_in_transaction_
  session_timeout`, image tag) — the local↔CI divergence in attempt #3 suggests an environmental factor worth
  capturing first (add a one-off diagnostic that logs `current_setting('lock_timeout')`, pool size, and
  `pg_backend_pid()` on the runner before committing to a shape).
- If in-process contention stays intractable, evaluate giving notification-service its **own** integration job
  (not bundled) so a hang can't be masked/attributed to siblings, or asserting exclusivity via a smaller,
  fully-controlled harness.

## Related

- Query under test: `notification-service/.../adapter/outbound/persistence/jpa/delivery/NotificationDeliveryJpaRepository.java` (`findPendingDueForRetry`, SKIP-LOCKED hint) + `DeliveryRepositoryImpl.findAndLockPendingDueForRetry`.
- Already-shipped sibling coverage (BE-528): `DeliverySkipLockedClaimIntegrationTest.claimReturnsOnlyPendingDueOrdered` (plain claim semantics), `SlackChannelAdapterCircuitBreakerTest` (breaker transitions), `DeliveryRetrySchedulerTest`.
- Memory: `env_ci_flake_is_a_hypothesis_not_a_verdict`, `env_test_fixture_impossible_input_proves_nothing`, `project_testcontainers_docker_desktop_blocker`, `env_wms_notification_seed_cluster_ci_flake` (IT-lane resource/serialisation), `platform/testing-strategy.md`.
