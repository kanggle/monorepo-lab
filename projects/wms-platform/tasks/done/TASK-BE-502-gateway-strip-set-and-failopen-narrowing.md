# Task ID

TASK-BE-502

# Title

wms gateway 가 **자기 스펙이 명시한 strip 대상**(`X-Account-Id`·`X-Tenant-Id`·`X-Roles`)을 실제로는 지우지 않는다 — 더불어 `FailOpenRateLimiter` 가 **모든 `Throwable`** 을 "Redis 다운" 으로 삼켜 레이트리밋을 조용히 무력화하고 프로그래밍 버그까지 은폐한다

# Status

done

# Owner

wms-platform

# Task Tags

- security
- gateway
- observability

---

# Dependency Markers

- **선행 없음** — 단독 착수 가능.
- **자매 task (동일 진단에서 파생, 비차단)**: `TASK-BE-501`(ecommerce gateway `X-Actor-Id` strip + 403 매핑). 같은 게이트웨이 드리프트 진단의 산물이며 **같은 PR 로 묶어 랜딩**한다. 파일이 겹치지 않아 순서 의존 없음.
- **참조 구현**: scm-platform gateway. 두 결함 모두 **scm 에는 이미 올바른 구현이 있다** — 즉 본 task 는 새 설계가 아니라 **누락된 수정의 역-이식**이다.
- **후속 (별건)**: `libs/java-gateway` 추출. 본 task 가 **선행**이어야 한다 — 갈라진 동작을 그대로 합치면 어느 도메인의 보안 동작이 바뀌는 것이 "리팩토링" 으로 위장된다.

---

# Goal

게이트웨이 드리프트 진단(2026-07-11) 에서 확정된 wms gateway 의 결함 2건을 고친다.

## 결함 A — strip 집합이 자기 스펙을 위반

`projects/wms-platform/specs/services/gateway-service/overview.md` L23 (§ Responsibilities):

> **Identity header pipeline** — strip client-supplied headers (**`X-Account-Id`**, **`X-Tenant-Id`**, **`X-Roles`**); re-set from verified JWT claims.

그런데 `IdentityHeaderStripFilter.IDENTITY_HEADERS` 의 실제 내용은:

```java
"X-User-Id", "X-User-Email", "X-User-Role", "X-Actor-Id", "X-Account-Type"
```

**스펙이 이름 붙여 요구한 세 개(`X-Account-Id`, `X-Tenant-Id`, `X-Roles`)가 전부 없다.** 스펙에 없는 것들만 지우고 있다.

⇒ 클라이언트가 `X-Tenant-Id: victim-tenant` / `X-Roles: ADMIN` / `X-Account-Id: <남의-계정>` 을 붙여 보내면 게이트웨이를 **그대로 통과**한다. wms enrich 필터도 이 셋을 설정하지 않으므로(설정: `X-User-Id`, `X-Actor-Id`, `X-User-Email`, `X-User-Role`) **덮어쓰기 방어도 없다.**

**폭발 반경 (정직하게)**: wms `src/main` 에서 이 세 헤더를 **읽는 코드는 현재 0건**이다 — 오늘 익스플로잇되지 않는 **장전 안 된 총**이다. 그러나 같은 서비스가 **`X-Actor-Id` 는 12개 컨트롤러에서 신뢰**한다(`ACTOR_HEADER` 상수). 즉 "게이트웨이가 걸러줬을 것이다" 라는 전제 위에 컨트롤러를 짜는 것이 **이 코드베이스의 확립된 관행**이며, 누군가 `X-Tenant-Id` 를 같은 전제로 읽는 순간 **테넌트 경계가 뚫린다**(ADR-MONO-024 D2 tenant confinement 위반). 리더가 0건인 지금이 가장 싸게 막을 시점이다.

또한 scm/fan 은 이 셋을 **이미 strip 한다**(scm 10개, fan 8개). wms 만 빠졌다.

## 결함 B — `FailOpenRateLimiter` 가 모든 예외를 삼킨다

wms `FailOpenRateLimiter:40-46`:

```java
return delegate.isAllowed(routeId, id)
        .onErrorResume(err -> {                          // ← 모든 Throwable
            log.warn("Rate-limit backing store failed; failing open ...");
            return Mono.just(new Response(true, ...));   // ← 무조건 통과
        });
```

`onErrorResume(err -> ...)` 는 **predicate 가 없다.** `RedisConnectionFailureException` 뿐 아니라 `NullPointerException`, `ClassCastException`, Lua 인자 오류, 그 무엇이든 **"Redis 가 죽었나 보다" 로 해석되어 요청이 통과**한다.

두 가지가 동시에 나쁘다:
1. **레이트리밋이 조용히 무력화된다** — 필터 체인에 프로그래밍 버그가 하나 있으면 wms 는 **레이트리밋 없는 게이트웨이**가 되지만, 로그에는 "backing store failed" 만 찍혀 Redis 를 의심하게 만든다.
2. **버그가 은폐된다** — 5xx 로 터져 관측에 잡혀야 할 결함이 WARN 한 줄로 묻힌다.

scm/fan/ecommerce 는 **이미 고쳐져 있다**:

```java
.onErrorResume(FailOpenRateLimiter::isRedisFailure, err -> { ... })   // scm:65
.doOnError(err -> { unexpectedErrorCounter.increment(); ... })        // scm:71 — 비-Redis 는 전파
```

`isRedisFailure`(scm:84-100) 는 cause 체인을 따라가며 `RedisConnectionFailureException` / `QueryTimeoutException` / `RedisSystemException` / `RedisException` 만 인정한다(self-cause 루프 가드 포함). 그 외는 전파. 메트릭 2종(`gateway_ratelimit_redis_unavailable_total`, `gateway_ratelimit_unexpected_error_total`)으로 구분 관측한다.

**즉 이 버그는 이미 한 번 고쳐졌는데 wms 에만 반영되지 않았다.** 복붙 게이트웨이가 치르고 있는 대가의 실물이다.

---

# Scope

## In Scope

1. `IdentityHeaderStripFilter.IDENTITY_HEADERS` 에 **`X-Account-Id`, `X-Tenant-Id`, `X-Roles` 추가** (스펙 L23 이 요구하는 그대로).
2. `FailOpenRateLimiter` 를 scm 구현으로 **수렴** — `isRedisFailure` predicate 협소화 + 비-Redis 오류 전파 + 메트릭 2종. `RateLimitConfig.failOpenRateLimiter(...)` 빈이 `MeterRegistry` 를 주입받도록 시그니처 변경.
3. 두 결함의 **회귀 테스트**. 각 테스트는 픽스를 되돌리면 **반드시 실패해야 한다**(AC-4).

## Out of Scope

- **`X-Token-Type` / `X-Scopes` 추가** — scm 만 strip 한다. wms 스펙도 정책도 요구하지 않는다. 근거 없이 넓히지 않는다.
- **strip 한 헤더들의 enrich(재주입)** — 스펙 L23 은 "strip ...; re-set from verified JWT claims" 라고 쓰지만, `X-Account-Id`·`X-Roles` 는 **매핑할 클레임 규약이 어디에도 정의돼 있지 않고 리더가 0건**이며, `X-Tenant-Id` 도 리더 0건이다. **아무도 읽지 않는 헤더를 주입하면 다음 사람이 그것을 신뢰한다** — 공급 없이 방어만 하는 것이 지금 정확한 상태다. 리더가 생길 때 enrich 를 별건으로 추가하고, 그때 스펙의 "re-set" 절이 무엇을 뜻하는지도 확정한다. **이 판단을 스펙에 각주로 남긴다**(조용히 절반만 이행하지 않는다).
- **ecommerce 게이트웨이** — `TASK-BE-501`.
- **fan / scm 게이트웨이** — **결함 없음(진단 결과)**. fan 에 `entitled_domains` dual-accept 가 없는 것은 **정확한 상태**다 — `fan` 은 `ProductCatalog.ENTRIES`(= `{iam, wms, scm, erp, finance, ecommerce}`) 에 없어 구독 가능 도메인이 아니다(V0019 백필도 `('wms','scm','erp','finance')` 만 시드, 전-도메인 테스트 테넌트 `omni-corp` 도 fan 미구독, `fan-platform` = `B2C_CONSUMER`). fan 은 엔타이틀먼트 평면 **밖**이다.
- **`libs/java-gateway` 추출** — 별건. 본 task 가 선행이다.

---

# Acceptance Criteria

- [ ] AC-1 — wms `IdentityHeaderStripFilter.IDENTITY_HEADERS` 가 스펙 L23 의 세 헤더(`X-Account-Id`, `X-Tenant-Id`, `X-Roles`)를 **전부** 포함한다. 기존 5개도 유지(회귀 없음).
- [ ] AC-2 — 필터를 실제로 태워, 클라이언트가 붙인 `X-Tenant-Id` / `X-Roles` / `X-Account-Id` 가 **mutated request 에서 사라짐**을 단언. 상수 목록만 비교하는 테스트는 "필터가 정말 지우는가" 를 증명하지 못하므로 불충분하다. **JWT 없는 경로**(public/webhook)도 커버 — enrich 가 no-op 이라 strip 이 유일한 방어선이다.
- [ ] AC-3 — `FailOpenRateLimiter` 가 (a) `RedisConnectionFailureException` 등 Redis-계열 → **fail-open**(allowed, `X-RateLimit-Remaining: -1`) + `gateway_ratelimit_redis_unavailable_total` 증가, (b) `NullPointerException` 등 비-Redis → **전파**(fail-open 하지 않음) + `gateway_ratelimit_unexpected_error_total` 증가. cause 체인에 감싸인 Redis 예외도 인식(self-cause 루프에 빠지지 않음).
- [ ] AC-4 — **비-공허성(non-vacuity) 증명**: 픽스를 되돌린 상태(strip 3개 제거 / `onErrorResume(err -> ...)` 로 복귀)에서 새 테스트가 **실제로 실패**함을 확인하고 PR 에 기록. "테스트가 통과했다" 는 픽스의 증거가 아니다 — **결함을 주입했을 때 무는지**가 증거다.
- [ ] AC-5 — 스펙 `overview.md` L23 에 "strip 은 이행, re-set 은 리더 부재로 보류" 각주 추가(Out of Scope 의 판단을 문서에 남긴다).
- [ ] AC-6 — 기존 wms gateway 테스트 전부 GREEN. CI `Build & Test (JDK 21, Linux)` + `E2E (gateway-master live-pair smoke)` GREEN.

---

# Related Specs

- `projects/wms-platform/specs/services/gateway-service/overview.md` L23 — **결함 A 의 권위 출처** (스펙이 코드를 반박한다)
- [`platform/api-gateway-policy.md`](../../../../platform/api-gateway-policy.md) § Identity Header Handling (L72–77)
- `projects/wms-platform/apps/gateway-service/src/main/java/com/wms/gateway/filter/IdentityHeaderStripFilter.java` (L24–30 — 결함 A)
- `.../gateway/ratelimit/FailOpenRateLimiter.java` (L38–46 — 결함 B)
- `.../gateway/config/RateLimitConfig.java` (L48–52 — 빈 시그니처 변경 지점)
- **참조 구현**: `projects/scm-platform/apps/gateway-service/.../ratelimit/FailOpenRateLimiter.java` (L42–100) · `.../filter/IdentityHeaderStripFilter.java` (L24–35)
- **패턴 실증**: wms `admin-service`/`master-service` 컨트롤러 12곳의 `ACTOR_HEADER = "X-Actor-Id"` — "게이트웨이가 걸러줬을 것" 이라는 전제가 이미 코드에 박혀 있다
- ADR-MONO-024 § D2 (tenant confinement) — `X-Tenant-Id` 위조가 뚫는 경계

# Related Contracts

- 계약 변경 **없음**. 두 결함 모두 **정상 요청의 관측 가능한 동작을 바꾸지 않는다**:
  - 결함 A: 정상 클라이언트는 이 세 헤더를 보내지 않는다(정책상 보낼 수 없는 헤더). 위조 요청만 영향받는다.
  - 결함 B: Redis 정상 시 동작 불변. Redis 장애 시 fail-open 도 불변. **바뀌는 것은 "비-Redis 버그가 났을 때"** 뿐이며, 그때 통과시키던 것을 이제 5xx 로 드러낸다 — 이는 결함 노출이지 계약 변경이 아니다.
- 신규 메트릭 2종은 additive.

---

# Target Service

- `wms-platform` / `gateway-service`

---

# Architecture

- `FailOpenRateLimiter` 는 scm 과 **동일한 구조**로 맞춘다(생성자 `(RedisRateLimiter, MeterRegistry)`, `static isRedisFailure(Throwable)`, 메트릭 상수 2종). 이유: `libs/java-gateway` 추출 시 이 클래스는 **무-파라미터 그대로 추출 가능** 대상이므로, 지금 시그니처를 수렴시켜 두면 추출이 순수 이동이 된다.
- `MeterRegistry` 는 Spring Boot Actuator 가 이미 제공한다(wms gateway 는 actuator 의존 보유 — 확인 필요. 없으면 의존 추가가 In Scope).

---

# Edge Cases

- **`X-Tenant-Id` 를 보내는 정상 클라이언트가 있는가?** — 없어야 한다. wms 리더 0건. e2e/IT 픽스처가 이 헤더를 수동 세팅해 통과 중이라면 그 테스트가 **원래 잘못된 것**이므로 함께 고친다(특히 `E2E (gateway-master live-pair smoke)` 확인).
- **cause 체인 self-loop** — `t.getCause() == t` 인 예외가 존재한다. scm 구현에 이미 가드가 있다(`if (cur == cur.getCause()) break;`). **복사할 때 빠뜨리면 무한루프**다.
- **`RedisException`(lettuce) vs Spring 예외** — 둘 다 인정해야 한다. lettuce 가 raw 로 올라오는 경로가 있다.
- **fail-open 이 여전히 필요하다** — 레이트리밋은 정확성 경계가 아니라 soft protection이다. Redis 장애로 엣지 전체가 죽으면 안 된다. 본 task 는 fail-open 을 **없애는 것이 아니라 그 범위를 Redis 장애로 한정**하는 것이다.
- **메트릭 이름 충돌** — scm 과 동일한 메트릭명을 쓴다. 서비스가 다르므로 스크레이프 라벨(`service`)로 구분된다. 동일명이 오히려 대시보드 재사용에 유리하다.

---

# Failure Scenarios

| # | 시나리오 | 기대/완화 |
|---|---|---|
| 1 | strip 상수 목록만 단언하는 테스트 작성 | 필터가 **정말 지우는지** 미증명. AC-2 가 mutated request 를 직접 본다 |
| 2 | `isRedisFailure` 의 self-cause 가드 누락 | 무한루프. Edge Case 로 명시, 테스트로 커버 |
| 3 | fail-open 자체를 제거 | Redis 장애 = 엣지 전면 5xx. **의도가 아니다** — 범위만 좁힌다 |
| 4 | 테스트 통과를 픽스의 증거로 제시 | 공허할 수 있다. **AC-4** mutation check 가 진짜 증거 |
| 5 | strip 만 하고 스펙의 "re-set" 절을 조용히 무시 | AC-5 가 각주를 강제 — 판단을 문서에 남긴다 |
| 6 | scm strip 집합(10개) 통째 복사 | 근거 없는 확대. 스펙이 요구하는 3개만(Out of Scope) |
| 7 | `MeterRegistry` 빈 부재로 컨텍스트 기동 실패 | actuator 의존 사전 확인. 없으면 의존 추가 |

---

# Test Requirements

- **strip**: `ServerWebExchange` 를 실제로 필터에 태워 mutated request 의 헤더 부재 단언. JWT 있는 경로 + **JWT 없는 경로** 둘 다.
- **rate limiter**: `RedisRateLimiter` 를 mock 해 (a) Redis 예외 → allowed + 카운터, (b) NPE → 전파 + 카운터, (c) cause 체인에 감싸인 Redis 예외 → allowed. `SimpleMeterRegistry` 로 카운터 단언.
- **AC-4 (mutation check)** 는 수동이라도 반드시 수행하고 PR 본문에 결과를 붙인다.
- Docker-free 로 전부 가능한 범위(단위/슬라이스) — 로컬 Windows Testcontainers 는 npipe flake 이므로 IT 는 **CI Linux 가 권위**.

---

# Definition of Done

- [ ] strip 집합에 3개 추가 + 회귀 테스트(mutated request 단언)
- [ ] `FailOpenRateLimiter` scm 구현으로 수렴 + 메트릭 2종 + 회귀 테스트
- [ ] AC-4 mutation check 수행 및 PR 기록
- [ ] 스펙 `overview.md` L23 각주(re-set 보류 판단)
- [ ] CI GREEN (Build & Test + gateway-master live-pair smoke)
- [ ] `projects/wms-platform/tasks/INDEX.md` 갱신

---

# Provenance

2026-07-11. `TASK-MONO-347`(finance/erp 게이트웨이 부재 드리프트) 조사 중 **"그럼 존재하는 게이트웨이 5개는 서로 같은가?"** 를 확인하려 전수 진단을 돌렸다. 복붙일 거라는 예상과 달리 **세 혈통**(wms·scm·fan = 공유 가문 / ecommerce = 부분 공유 + 자체 확장 / iam = 완전 독립 구현)이었고, 그 사이에 **보안 격차 5건**이 있었다.

**본 task 의 결함 B 가 진단 전체를 정당화하는 실물 증거다**: `FailOpenRateLimiter` 의 `onErrorResume` 협소화는 **이미 한 번 수행된 수정**인데(scm/fan/ecommerce 반영) **wms 에만 전파되지 않았다.** 복붙 코드는 버그 수정이 전파되지 않는 순간부터 조용히 갈라지고, **어느 것이 맞는 버전인지 코드만 봐서는 알 수 없다.** 실제로 아무도 모르고 있었다.

그래서 `libs/java-gateway` 추출은 DRY 리팩토링이 아니라 **보안 수렴 작업**이며, 수렴 전에 각 결함을 개별로 고쳐 놓아야 추출 PR 이 "행동 불변" 을 정직하게 주장할 수 있다. 큰 리팩토링 PR 안에 보안 수정이 섞이면 리뷰가 불가능하고 롤백이 전부-아니면-전무가 된다.

분석=Opus 4.8 / 구현 권장=Opus (보안 경계 + 예외 협소화. 특히 self-cause 가드와 "mutated request 를 실제로 확인" 두 지점이 기계적 복사로는 놓치기 쉽다).
