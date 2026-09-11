# TASK-BE-529 — notification-service: CI-robust SKIP-LOCKED exclusivity IT (two-claimant no-double-claim)

- **Type**: TASK-BE (test-coverage — deferred from TASK-BE-528 AC-2)
- **Status**: done
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

## CORRECTION

> 🔴 **이 절은 더하기만 한다** — 위의 어떤 관측도 고치거나 지우지 않는다. 위 기록은 그 날짜에
> 대한 사실이고, 아래는 **그 뒤에 일어난 일**이다(`TASK-MONO-591` 의 CORRECTION 규약).

### 2026-09-11 UTC — **재발 1건. 그리고 이 티켓의 설계가 그 자리에서 동작했다**

`DeliverySkipLockedClaimIntegrationTest` 가 `main` 의 post-merge CI 에서 한 번 빨갰다
(커밋 `127e2d916` — `TASK-PC-FE-281`, 콘솔 TypeScript 만 바꾼 커밋).

```
DeliverySkipLockedClaimIntegrationTest > SKIP-LOCKED exclusivity: … FAILED
    org.hibernate.QueryTimeoutException at DeliverySkipLockedClaimIntegrationTest.java:140
BUILD FAILED in 2m 50s
```

#### 🔵 먼저 — 이것은 **AC-1 이 설계한 그대로**다

AC-1 이 요구한 것은 *"every lock wait is bounded … so a regression (or a non-skip-locked query)
fails **fast and loud**, never at the 30-min job timeout"* 이다. 실제로 일어난 일:
**유계 타임아웃이 발화**했고(`QueryTimeoutException`), **2분 50초**에 죽었다.

🔴 **그러므로 이 재발은 「BE-529 가 회귀했다」가 아니다.** 유계 타임아웃이 없었다면 이 세션은
30분짜리 잡 타임아웃을 봤을 것이고, 원인은 훨씬 안 보였을 것이다. **이 티켓이 산 값이 그것이다.**

#### 판정 — **환경 요인**(그리고 그것은 추정이 아니라 측정이다)

이 저장소의 규율은 *"「flake=인프라」는 가설"* 이다. 그래서 가설로 두지 않고 갈랐다:

| 축 | 결과 |
|---|---|
| **같은 트리 재실행** | 🟢 **통과** (`gh run rerun --failed`, 같은 커밋) |
| 그 커밋의 `wms-platform` 변경 | **0건** (바꾼 것은 console-web `.tsx` 4개 + 태스크 파일 2개) |
| 실패 지문 | 전부 **연결 수준** — `An I/O error occurred while sending to the backend` ×5 · `Closed by interrupt` ×2. **단언 실패가 아니다** |
| 직전에 wms 를 **실제로** 바꾼 커밋(`c12bedcb3`) | 같은 잡 🟢 **success** |

⇒ diff 가 원인이 아니라는 것은 **재실행이 직접 보여 준다**(트리가 같다). 현재 `127e2d916` 은
CI · Nightly E2E · Vercel 전부 success 다.

#### ⚪ 이 관측이 **말하지 않는** 것

- 🔴 **표본 하나다.** 「이 테스트는 flaky 하다」는 성질로 승격시키지 마라 — 이 저장소가 이름 붙인
  «측정 하나를 성질로 승격 금지» 다. 여기 적는 것은 **관측 1건**이고, 두 번째가 생기면 그때
  비율을 말할 수 있다.
- 🔴 재실행이 통과했다는 것이 **「다시는 안 난다」를 뜻하지 않는다.** 그것이 증명한 것은
  «그 커밋의 diff 가 원인이 아니다» 하나다.
- ⚪ **무엇이 러너를 그 상태로 만들었는지는 안 쟀다**(동시 실행 레인의 자원 경합? 컨테이너
  teardown?). 🔵 `env_wms_notification_seed_cluster_ci_flake`(IT-lane 자원/직렬화)가 인접 축이다.

#### 다음 사람에게

🔵 **여기서 새 티켓을 파지 않았다** — 관측 1건이고, 이 티켓의 설계는 **의도대로 동작했다**.
🔴 다만 **두 번째 재발이 생기면** 그때는 부류가 바뀐다(간헐이 아니라 비율이 된다) ⇒ 그때
이 절에 한 줄을 더하고, 세 번째면 별도 티켓이다. **이 절이 그 카운터다.**
