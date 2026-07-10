# Task ID

TASK-BE-497

# Title

`CrossTenantRateLimitIsolationIntegrationTest` (M7 AC-1) 가 CI 러너 포화 시 간헐 실패 — rate limiter 가 **설계대로 fail-open** 하는데 테스트는 **강제 집행(429)** 을 단정한다. Redis 지연으로 양 요청이 열리면 `429 기대 → 404 수신`. 테스트를 fail-open 계약과 정합시켜 결정론화

# Status

ready

# Owner

ecommerce-microservices-platform

# Task Tags

- test
- flake
- gateway

---

# Dependency Markers

- **선행 없음** — 단독 착수 가능.
- **관련 (비차단)**: `TASK-MONO-343`(머지됨, `df216a75f`) 은 **doc-only main 푸시**에서 이 레인이 도는 것을 막는다 — 노출 **빈도**를 줄일 뿐 원인을 없애지 않는다. **코드 PR 에서는 여전히 밟는다.** 본 task 가 원인을 제거한다.
- **원 구현**: `TASK-BE-405`(M7 rate-limit realization) — 실패 테스트와 `FailOpenRateLimiter` 의 출처. 그 설계 결정(soft protection, fail-open)은 **정당하며 바꾸지 않는다**; 본 task 는 **테스트**를 그 계약에 맞춘다.

---

# Goal

`projects/ecommerce-microservices-platform/apps/gateway-service/.../integration/CrossTenantRateLimitIsolationIntegrationTest` 의 `AC-1: tenant A 의 burst(429)가 tenant B 의 버킷을 소모하지 않는다` 가 CI 에서 간헐 실패한다. 실패 지문은 항상 동일하다:

```
java.lang.AssertionError: Status expected:<429 TOO_MANY_REQUESTS> but was:<404 NOT_FOUND>
  at CrossTenantRateLimitIsolationIntegrationTest ... line 117 (2번째 요청)
```

동반 신호: HikariCP `TimeoutException`(같은 CI 잡의 **다른** 서비스 IT — order/payment/product-service 가 한 잡에 묶여 있음) + Testcontainers 매핑 포트 다수(~69개) `Connection refused` = **러너 리소스 포화**.

**근본 원인 (테스트 ↔ 설계 불일치)**:

- `FailOpenRateLimiter`([main/.../ratelimit/FailOpenRateLimiter.java](../../apps/gateway-service/src/main/java/com/example/gateway/ratelimit/FailOpenRateLimiter.java)) 는 TASK-BE-405 M7 명령대로 **의도적으로 fail-open** 한다: delegate(`RedisRateLimiter`)가 `RedisConnectionFailureException` / `QueryTimeoutException` / `RedisSystemException` / `RedisException` 을 던지면 `onErrorResume` 이 `Response(true, …)`(= **허용**)를 돌려주고 `gateway_ratelimit_redis_unavailable_total` 을 증가시킨다. rate limiting 은 soft protection 이라 카운터 저장소를 잃어도 정상 트래픽을 막지 않는다 — 이 설계는 정당하다.
- 테스트는 **강제 집행**을 단정한다. line 114~117 의 2번째 요청이 `429` 를 정확히 기대한다.
- 러너가 포화되면 Redis 왕복이 Lettuce/limiter timeout 을 초과 → limiter 가 **설계대로 열린다** → 요청이 (테스트가 reachable JWKS mock 으로 repoint 한) 라우트에 도달 → **404**.
- **두 assertion 의 비대칭이 결함을 가린다**: 1번째 요청(line 107~111)은 `≠429` 만 요구한다. Redis 가 처음부터 느리면 **양쪽 요청이 모두 fail-open** → 둘 다 404 → **1번째는 약한 assertion 으로 통과(429 아님 ✓)** 하며 "limiter 가 아예 작동 안 함" 을 **가리고**, 2번째의 강한 assertion(`정확히 429`)만 `404` 로 뒤집힌다. 관측된 패턴과 정확히 일치한다.

**증거 (flake 확정, 2026-07-10)**:

| # | 확인 | 결과 |
|---|---|---|
| 1 | rerun(코드 변경 0) | 양 레인 GREEN → flake |
| 2 | main 재현 | main tip `9a5e4b120` + base `b6b7d4c21` 에서 **글자 그대로 동일** assertion 실패 |
| 3 | 교대성 | 레인이 `a5e3b4800 ✅ → b6b7d4c21 ❌ → c8d2d5fd8 ✅ → 9a5e4b120 ❌` 로 왕복(결정론적 회귀 아님) |
| 4 | 인과 부재 | green→red 경계 커밋 2개가 건드린 37파일 중 `libs/`·`projects/ecommerce/` **0개** |

**목표**: 테스트를 `FailOpenRateLimiter` 의 계약과 정합시켜, **Redis 가 실제로 강제 집행 중일 때만** 429 를 단정하고, **fail-open 이 일어나면 잘못된 로직 실패(404 vs 429)가 아니라 명시적 인프라 신호로 드러나게** 한다. limiter 프로덕션 코드는 무변경.

---

# Scope

## In Scope

1. `CrossTenantRateLimitIsolationIntegrationTest` — 결정론화. 방향(택1 또는 조합, 구현자 판단):
   - **(권장) enforcing 워밋업 게이트** — 실제 assertion 전에 "throwaway drain→reject" 1사이클을 돌려 **429 가 관측될 때까지** (Awaitility, 짧은 bounded 대기) 확인한다. 429 가 안 나오면 Redis 가 enforcing 상태가 아니므로 warm-up 단계에서 걸러진다(테스트 로직 실패로 오인되지 않음).
   - **fail-open 카운터 단언** — 테스트 종료 시 `gateway_ratelimit_redis_unavailable_total == 0` 을 단정(MeterRegistry 주입). >0 이면 "Redis 가 blip 했다 = 인프라, 로직 아님" 이 **명시적**으로 드러난다. warm-up 과 조합하면 이상적.
   - **약한 1번째 assertion 강화** — `≠429` 는 fail-open 404 를 통과시켜 결함을 가린다. 1번째 요청이 **정말로 버킷을 소모했는지**(예: 응답 헤더 `X-RateLimit-Remaining` 이 sentinel `-1`(=미집행)이 **아님**을 확인)를 함께 단정.
2. 러너 포화에 견디도록 Redis 준비/도달성 pre-flight(컨테이너 `isRunning` + ping)와 bounded 재시도. **`Thread.sleep` 고정 대기 금지** — Awaitility 조건 대기.

## Out of Scope

- **`FailOpenRateLimiter` / `OverrideAwareRateLimiter` / 라우팅 프로덕션 코드 변경** — fail-open 은 TASK-BE-405 의 정당한 설계 결정. 테스트만 고친다.
- **CI 잡 재분할**(order/payment/product 를 별 러너로) — 러너 포화 완화는 별개 인프라 관심사(monorepo-level). 본 task 는 포화 하에서도 **테스트가 거짓 실패하지 않게** 만드는 것.
- **`@Disabled` / `@Tag` 제외로 회피** — 커버리지를 죽이는 것은 해법이 아니다. 격리 속성(AC-1)은 계속 검증되어야 한다.
- **다른 서비스의 HikariCP timeout** — 같은 잡의 order/payment IT 소음일 뿐 본 실패의 원인이 아니다(gateway-service 는 JDBC 미사용, Redis 만).

---

# Acceptance Criteria

- [ ] AC-1 — 러너 포화(Redis 지연)를 인위 주입해도 테스트가 **로직 실패(429 vs 404)로 뒤집히지 않는다**. fail-open 이 발생하면 warm-up 게이트에서 걸러지거나 `redis_unavailable_total>0` 단언으로 **인프라 신호**로 드러난다.
- [ ] AC-2 — 격리 속성(tenant A 버킷 ≠ tenant B 버킷)은 여전히 실제 Redis 로 검증된다. mock 대체 금지.
- [ ] AC-3 — `FailOpenRateLimiter` 등 `apps/gateway-service/src/main/**` **byte-unchanged**(`git diff --numstat` 로 확인). 테스트/테스트-support 만 변경.
- [ ] AC-4 — 고정 `Thread.sleep` 0건. 대기는 Awaitility 조건 기반.
- [ ] AC-5 — 로컬 반복 실행(`--rerun-tasks` × N, 또는 스트레스 하)에서 결정론적. CI `Integration (ecommerce)` 레인 GREEN(선존 flake 와 구분되도록 rerun 없이 1차 GREEN 목표).

---

# Related Specs

- `apps/gateway-service` M7 rate-limit 스펙 / `TASK-BE-405`(원 구현, 실패 테스트 + `FailOpenRateLimiter` 의 출처).
- `docs/adr` 중 rate-limit degrade 관련(있으면) — fail-open 이 계약임을 재확인.

# Related Contracts

None — 테스트 결정론화. API/이벤트 계약 무변경.

---

# Edge Cases

- **1번째 요청도 fail-open 이면 (핵심)** — Redis 가 처음부터 죽어 있으면 양 요청이 열려 둘 다 404. `≠429` 인 1번째 assertion 이 이를 **통과시켜 가린다**. warm-up 게이트가 없으면 "limiter 미작동" 이 2번째 요청까지 잠복한다. AC-1 의 핵심 방어 지점.
- **부분 fail-open** — 1번째는 Redis 정상(버킷 소모), 2번째 순간 Redis blip → fail-open 404. 이 경우 카운터가 정확히 잡아낸다(`redis_unavailable_total==0` 단언이 유효).
- **replenish 타이밍** — `replenishRate=1/s`, `burst=1`. 두 요청 사이 1초 이상 경과하면 버킷이 정상적으로 리필되어 2번째도 허용(429 아님)될 수 있다. warm-up 이 시간을 소비하면 이 창에 걸릴 수 있으므로, **assertion 요청 2개는 인접 실행**하고 warm-up 은 그 앞에서 별도 키/사이클로 끝낼 것.
- **카운터 공유** — `gateway_ratelimit_redis_unavailable_total` 은 앱 전역. 같은 컨텍스트의 다른 테스트가 증가시켰다면 절대값 0 단언이 깨진다 → 테스트 시작 시점 스냅샷 대비 **델타** 로 단언하거나 per-test 프레시 registry.
- **Awaitility 상한** — bounded(예: 5~10s)로. 무한 대기는 러너 포화 시 잡 timeout(30분) 유발.

---

# Failure Scenarios

| # | 시나리오 | 기대/완화 |
|---|---|---|
| 1 | limiter 프로덕션 코드를 "고쳐" fail-open 제거 | TASK-BE-405 설계 위반 + edge 회귀. Out of Scope 명시 |
| 2 | `@Disabled` 로 회피 | AC-1 격리 커버리지 소멸. Out of Scope |
| 3 | `Thread.sleep` 로 "안정화" | 포화 시 여전히 flake + 잡 시간 낭비. AC-4 금지 |
| 4 | 카운터 절대값 0 단언(델타 아님) | 형제 테스트 증가분에 오손. Edge Cases |
| 5 | warm-up 이 replenish 창을 넘김 | 2번째 요청이 리필로 허용 → 새 flake. Edge Cases(인접 실행) |
| 6 | mock Redis 로 대체 | AC-2 위반(격리 속성 미검증) |

---

# Test Requirements

- 로컬 결정론: `./gradlew :projects:ecommerce-microservices-platform:apps:gateway-service:integrationTest --tests '*CrossTenantRateLimitIsolation*' --rerun-tasks` 를 N회 반복 GREEN. Redis 지연 주입(예: Toxiproxy latency, 또는 컨테이너 pause 순간)으로 fail-open 경로를 **의도적으로** 유발했을 때 로직 실패가 아니라 warm-up/카운터로 걸러짐을 확인.
- Windows 로컬 Testcontainers 는 npipe flake 가능 → **CI Linux 가 권위**(monorepo-lab 규율).
- AC-3: `git diff --numstat origin/main -- apps/gateway-service/src/main` 이 **빈 출력**.

---

# Definition of Done

- [ ] 테스트 결정론화(warm-up 게이트 + fail-open 카운터 델타 단언 권장), 프로덕션 코드 무변경.
- [ ] CI `Integration (ecommerce)` GREEN(rerun 없이).
- [ ] `projects/ecommerce-microservices-platform/tasks/INDEX.md` done entry.

---

# Provenance

Surfaced 2026-07-10 — `TASK-MONO-345`(서비스 맵 드리프트) PR #2387 의 CI 에서 `Integration (ecommerce)` 가 RED 였고, 머지 전 3차원 검증 (c)항을 위해 flake 여부를 실증하는 과정에서 지문(`Cross-tenant rate-limit AC-1: 429 기대 → 404`)을 역추적했다. `FailOpenRateLimiter` 소스를 읽고 **테스트가 fail-open 계약에 무지하다**는 것이 원인으로 특정됐다. 같은 날 이 레인이 **3건의 무관한 doc-only main 커밋**을 RED 로 만들었고(TASK-MONO-343 의 근거와 겹침), 그때마다 작업자가 "내 탓인가" 를 반증하는 데 시간을 썼다 = flake 소진(desensitization)의 실물. 343 이 doc-only 노출을 막았으나 코드 PR 노출은 남아 원인 제거가 필요하다.

분석=Opus 4.8 / 구현 권장=**Sonnet** (테스트 결정론화 — 상태기계·계약 설계 없음. 단, Edge Cases 의 "1번째 요청도 fail-open" 과 "replenish 타이밍" 두 함정은 반드시 읽고 착수. Redis 지연 주입 검증은 Toxiproxy 경험이 있으면 수월).
