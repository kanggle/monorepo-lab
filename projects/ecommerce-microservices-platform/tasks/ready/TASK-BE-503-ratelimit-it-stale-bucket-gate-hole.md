# Task ID

TASK-BE-503

# Title

Cross-tenant rate-limit IT 의 간헐 RED 실근인 제거 — `cleanRateKeys()` 는 **0건을 지우고 있고**(SCG 키 접두어 오추정), Awaitility 게이트가 **stale-bucket 사이클을 채택**한다 (TASK-BE-497 하드닝의 구멍)

# Status

ready

# Owner

ecommerce-microservices-platform

# Task Tags

- bug
- test
- flaky

---

# Goal

`CrossTenantRateLimitIsolationIntegrationTest` (M7, AC-1) 이 **자기 diff 와 인과가 없는 PR 들에서 반복적으로 RED** 다. 실패는 항상 동일하다:

```
Cross-tenant rate-limit 격리 통합 테스트 (M7) > AC-1: tenant A 의 burst(429)가 tenant B 의 버킷을 소모하지 않는다 FAILED
    java.lang.AssertionError: [tenant A first request is allowed (burst drain), not rate-limited]
    Expecting actual:
      429
    not to be equal to:
      429
        at CrossTenantRateLimitIsolationIntegrationTest.tenantABurst_doesNotConsumeTenantBBucket(:210)
```

**관측 이력** (둘 다 ecommerce 파일을 **0개** 건드리는 PR):
- PR #2402 (`TASK-BE-498`, iam 테스트 픽스처 TZ 수정) — 재실행 GREEN.
- PR #2408 (`TASK-BE-500`, iam 스펙 문서 + iam 테스트 1개) — 재실행 GREEN.

`TASK-BE-498` 종결 시 "**같은 테스트가 재차 RED 되면 착수**" 로 신호 기준을 명시해 두었고, **그 신호가 도착했다**(2회 독립 발생). 그리고 조사 결과 이건 **인프라 flake 가 아니라 테스트의 논리 결함 2건**이다.

## 근인 1 — `cleanRateKeys()` 가 아무것도 지우지 않는다 (SCG 키 접두어 오추정)

```java
// :150-155 (현행)
private void cleanRateKeys() {
    redisTemplate.keys("rate:ecommerce-gw:*")     // ← 0건 매치
            .flatMap(redisTemplate::delete)
            .collectList()
            .block(Duration.ofSeconds(5));
}
```

`rate:ecommerce-gw:<routeId>:t:<tenantId>` 는 **Redis 키가 아니다.** 그건 `TenantRouteRateLimitConfig#tenantRouteKeyResolver` 가 반환하는 값이고, Spring Cloud Gateway 는 그것을 `RedisRateLimiter.isAllowed(routeId, **id**)` 의 `id` 로 받아 **자기 키 형태로 감싼다**. SCG 4.2.0 `RedisRateLimiter.getKeys()` 의 문자열 레시피(jar 상수풀 `#401`)는

```
request_rate_limiter.{.}.
```

즉 실제 Redis 키는

```
request_rate_limiter.{rate:ecommerce-gw:product-service:t:tenant-a}.tokens
request_rate_limiter.{...}.timestamp
```

이고 **리터럴 `request_rate_limiter.{` 로 시작**한다. glob `rate:ecommerce-gw:*` 는 접두어 고정이므로 **매치 0건** → `cleanRateKeys()` 는 **완전한 no-op** 이다.

## 근인 2 — 게이트가 stale-bucket 사이클을 **채택**한다

```java
// :192-201 (현행)
enforced = Awaitility.await(...)
        .until(() -> { Cycle c = runCycle(tokenA, tokenB); last.set(c); return c; },
               c -> c.failOpenDelta() == 0.0 && c.a2().status() == 429);   // ← a1 에 대한 제약이 없다
```

게이트는 `a2 == 429`(버스트가 실제로 거부됨)와 fail-open 부재만 요구하고 **`a1` 을 전혀 제약하지 않는다.** 버킷이 시작부터 비어 있으면 `a1 = 429`, `a2 = 429` 가 되는데 — **그 사이클이 게이트를 통과해 채택되고**, 곧바로 `:208-210` 의 `a1 != 429` 단언이 터진다. **재시도 루프가 하필 테스트를 실패시키는 그 상태에서 종료한다.**

TASK-BE-497 은 주석이 말하듯 반대 방향만 막았다 — *"a slow cycle whose bucket refilled (a2 != 429) is simply discarded and retried"*. `a1` 이 이미 429 인 방향은 열려 있다.

## 두 근인이 맞물려 만드는 간헐성 (왜 평소엔 초록인가)

| 상황 | 결과 |
|---|---|
| **첫 사이클이 게이트를 만족** (평상시) | Redis 가 **진짜로** 비어 있어 `a1` 허용 → `a2=429` → 채택 → 전 단언 통과. **초록.** |
| **첫 사이클이 게이트를 못 넘음** (러너 포화 시 fail-open, 또는 버킷 refill 로 `a2≠429`) | Awaitility 재시도 → 그런데 `cleanRateKeys()` 가 **아무것도 안 지웠으므로** tenant A 버킷은 여전히 드레인 상태 → `a1=429, a2=429` → **게이트 통과 → 채택** → `a1 != 429` **단언 실패.** |

즉 **러너 포화는 방아쇠일 뿐 원인이 아니다.** 원인은 위 두 결함이고, 포화는 "첫 사이클이 게이트를 못 넘는" 조건을 만들어 결함을 **드러낼** 뿐이다. `cleanRateKeys()` 가 제대로 동작했다면 재시도는 항상 fresh bucket 에서 시작해 실패할 수 없었다.

> **"flake = 인프라" 는 가설이지 결론이 아니다.** BE-498 종결 노트가 이 실패를 "saturation" 으로 잠정 분류했으나(같은 잡에 Kafka `fetchMetadata` 타임아웃이 도배돼 있었다), 재실행-GREEN 은 인프라를 증명하지 않는다. 진짜 메시지는 **어설션 본문**에 있었다: `a1` 이 이미 429 라는 건 타이밍이 아니라 **상태 누수**다.

---

# Scope

## IN — 테스트 파일 1개 (`src/test/.../CrossTenantRateLimitIsolationIntegrationTest.java`)

1. **`cleanRateKeys()` 를 접두어-무관하게** 만든다. SCG 의 키 형태를 **추측하지 않는다** — 그 추측이 바로 이 버그다. Redis 컨테이너는 이 테스트 전용이므로 **전체를 비우고**, 비워졌음을 **확인**한다. SCG 가 키 형태를 바꿔도 썩지 않는다.
2. **게이트에 `a1 != 429` 를 추가**한다. stale-bucket 사이클은 채택이 아니라 **폐기·재시도** 되어야 한다 — BE-497 이 `a2` 방향에 대해 이미 채택한 설계 의도 그대로.
3. **`classifyTimeout` 에 stale-bucket 분기 추가.** 모든 사이클이 `a1 == 429` 로 끝났다면 그건 러너 포화가 아니라 **정리가 동작하지 않는다**는 뜻이므로, 그렇게 말해야 한다(다음 사람이 또 "flake" 로 오분류하지 않도록).
4. **정리 계약을 고정하는 가드 테스트 신설** — "요청을 보내 버킷 키를 만들고 → `cleanRateKeys()` → **키가 0개**". 이 테스트는 **원래 버그를 잡았을 유일한 가드**다(현행 패턴을 되돌리면 반드시 실패한다).

## OUT (의도적 제외 — 근거 포함)

- **운영 코드 무수정.** `FailOpenRateLimiter` / `OverrideAwareRateLimiter` / `TenantRouteRateLimitConfig` 는 **옳다.** fail-open 은 ADR 설계(TASK-BE-405)이고, keyResolver 의 반환값도 정상이다. **버그는 테스트에만 있다** — 테스트가 SCG 의 내부 키 형태를 잘못 추정했다.
- **fail-open 동작 변경 금지.** BE-497 이 명시했듯 rate limiting 은 soft protection 이고 Redis 불가 시 열리는 것은 **의도된 설계**다. 게이트가 `failOpenDelta == 0` 를 요구하는 구조도 유지한다.
- **`ENFORCING_WINDOW` / `REPLENISH_GUARD_MILLIS` 값 튜닝 금지.** 타임아웃을 늘리는 건 원인을 못 고치고 증상만 미루는 전형적 오답이다. 근인이 상태 누수이므로 시간을 더 줘도 낫지 않는다(오히려 stale 사이클이 더 많이 채택된다).
- wms `FailOpenRateLimiter`(TASK-BE-502 가 최근 수정) — 무관, 손대지 않는다.

---

# Acceptance Criteria

- **AC-1** — `cleanRateKeys()` 가 실제로 rate-limiter 키를 제거한다. **신설 가드 테스트**가 이를 단언한다(요청 → 키 존재 → 정리 → 키 0개).
- **AC-2** — Awaitility 게이트가 `a1 != 429` 를 요구한다. stale-bucket 사이클은 **채택되지 않고 재시도**된다.
- **AC-3** — `classifyTimeout` 이 "모든 사이클에서 `a1 == 429`" 를 **정리 실패**로 분류하고 그렇게 보고한다(포화로 오분류하지 않는다).
- **AC-4 (운영 무수정)** — `src/main` diff **0줄**. 테스트 전용 변경.
- **AC-5 (mutation-check)** — 신설 가드가 실제로 무는지 확인한다. `cleanRateKeys()` 의 패턴을 원래의 `"rate:ecommerce-gw:*"` 로 되돌려 주입했을 때 **정리 가드 테스트가 FAIL** 해야 한다. 통과하는 가드는 무는 가드가 아니다(MONO-348 · BE-493 · BE-500 교훈).
- **AC-6** — `:projects:ecommerce-microservices-platform:apps:gateway-service:integrationTest` GREEN. **빌드 상태가 아니라 `TEST-*.xml` 의 `tests=/skipped=/failures=`** 로 확인한다(Docker 다운 시 `BUILD SUCCESSFUL` + 전건 SKIPPED = 헛된 GREEN).
- **AC-7 (권위 레인)** — 로컬 Windows Testcontainers 는 권위가 아니다. **CI Linux `Integration (ecommerce, Testcontainers)` GREEN 이 판정 기준**이다.

---

# Related Specs

- `projects/ecommerce-microservices-platform/specs/services/gateway-service/` (rate-limit 설계)
- 선행: `TASK-BE-405`(rate limiter + fail-open 설계) · `TASK-BE-497`(saturation 하드닝 — 본 task 가 그 구멍을 메운다)

# Related Contracts

- 없음 (테스트 전용, 관측 가능한 API 동작 무변경)

---

# Edge Cases

- **Redis 컨테이너 전체 비우기의 안전성** — `@Container RedisContainer` 는 이 테스트 클래스 전용이고 게이트웨이는 rate-limiter 외에 이 Redis 에 아무것도 쓰지 않는다. 다른 데이터가 생기면 이 전제가 깨지므로, **정리 가드 테스트가 그 사실을 즉시 드러낸다**(키가 0 이 안 됨).
- **Awaitility `.ignoreExceptions()` 가 `AssertionError` 를 삼킨다** — `ignoreExceptions()` 는 `Throwable` 전체를 무시하므로, **정리 검증 단언을 Awaitility 루프 안에 넣으면 조용히 삼켜져 타임아웃으로 둔갑한다.** 정리 검증은 **루프 밖 독립 테스트**에 둔다. (이 함정을 모르고 루프 안에 넣으면 가드가 무는 것처럼 보이지만 실제로는 아무것도 못 잡는다.)
- **`a1 != 429` 게이트가 무한 재시도로 바뀔 위험** — 정리가 정상 동작하면 fresh bucket 의 `a1` 은 항상 허용되므로 이 조건이 루프를 늘리지 않는다. 정리가 고장 나면 타임아웃 → **AC-3 의 분기가 그 이유를 정확히 말한다**(무한 재시도가 아니라 명확한 진단).

# Failure Scenarios

- **증상만 덮기** — `ENFORCING_WINDOW` 를 늘리거나 `@RepeatedTest`/재시도 애노테이션으로 감싸면 초록이 되지만 **상태 누수는 남는다.** 금지(Scope § OUT).
- **운영 코드를 건드림** — "429 가 잘못 나온다" 를 limiter 버그로 오진해 `FailOpenRateLimiter` 를 고치면 **fail-open 설계(ADR)를 깨뜨린다.** 운영 코드는 옳다.
- **"인프라 flake" 로 재분류하고 재실행** — 지금까지 두 번 그렇게 넘겼다. 어설션 본문(`a1` 이 이미 429)이 상태 누수를 가리키고 있었다. **재실행-GREEN 은 인프라를 증명하지 않는다.**
- **로컬 GREEN 을 근거로 종결** — Windows Testcontainers 는 권위가 아니다(FLAKY). CI Linux 레인이 판정한다.
