# Task ID

TASK-MONO-351

# Title

`libs/java-gateway` 모듈 신설 + **Tier 1**(코드 바이트 동일 7 클래스) 추출 → wms·scm·fan 이관, ecommerce 는 `AllowedIssuersValidator` 채택 — ADR-MONO-048 D7 step 1

# Status

ready

# Owner

monorepo

# Task Tags

- refactor
- shared-library
- gateway
- security

---

# Dependency Markers

- **선행 (ACCEPTED)**: [`ADR-MONO-048`](../../docs/adr/ADR-MONO-048-shared-reactive-gateway-library.md) — 2026-07-11 사용자 명시 승인. **이 ADR 없이는 `libs/` 를 건드릴 수 없다**(`platform/shared-library-policy.md` § Change Rule). 결정 task = `TASK-MONO-350`(done).
- **선행 (머지됨)**: `TASK-BE-501` + `TASK-BE-502` (PR #2409 `b83adf4c3`). **이것들이 없었으면 이 task 는 불가능하다** — 갈라진 4개 구현을 합치면 어느 도메인의 보안 동작이 바뀌는 게 "리팩토링" 으로 위장된다. 특히 `FailOpenRateLimiter` 는 BE-502 **덕분에** 비로소 wms/scm/fan 3/3 코드 동일이 됐다.
- **후속 (본 task 랜딩 후 spawn)**: `TASK-MONO-352`(Tier 2 파라미터화) → `353`(ecommerce 이관) → `354`(finance/erp 게이트웨이 = `TASK-MONO-347` direction A 해소). **미리 만들지 않는다** — 각 step 의 범위는 직전 step 이 **실제로 증명한 것**에 맞춰 쓴다.
- **범위 밖**: `iam` 게이트웨이 (ADR D2 — 독립 구현. 통합은 추출이 아니라 **재작성**).

---

# Goal

**리액티브 공유 모듈이 실제로 서는지를 먼저 증명한다.** 기존 `libs/java-*` 는 전부 servlet 기반이거나 프레임워크 중립이다. 게이트웨이는 Spring Cloud Gateway / WebFlux 다. 이 조합이 Gradle·의존성·컨텍스트 기동 수준에서 문제없이 도는지가 **이 로드맵 전체의 첫 미지수**이므로, **변이가 0인 클래스들로만** step 1 을 구성해 그 리스크를 격리한다.

## Tier 1 = 코드 바이트 동일 (ADR § 1.2 측정, 재현 절차 포함)

주석 제거·패키지 정규화 후 **wms/scm/fan 3/3 동일**:

| 클래스 | 비고 |
|---|---|
| `ApiErrorEnvelope` | |
| `GatewayErrorHandler` | |
| `RequestIdFilter` | |
| `RetryAfterFilter` | |
| `SecurityConfig` | 3개 도메인 모두 `PUBLIC_PATHS` = `/actuator/health`, `/actuator/health/**`, `/actuator/info` 로 **동일** |
| `AllowedIssuersValidator` | **ecommerce 까지 4/4 동일** → ecommerce 도 이번에 채택 |
| `FailOpenRateLimiter` | **BE-502 가 수렴시킨 결과** 3/3 동일 (ecommerce 는 delegate 시그니처가 달라 step 3) |

**변이가 0이므로 파라미터가 필요 없다.** 이관은 순수 이동 + import 변경이어야 하며, 그 이상이면 이 task 의 범위를 벗어난 것이다.

## ⚠️ `SecurityConfig` 의 숨은 의존 (착수 즉시 확인)

wms/scm/fan 의 `SecurityConfig` 는 **`TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH`** 를 참조한다(`tenant_mismatch` → 403 `TENANT_FORBIDDEN` 매핑 분기). 그런데 `TenantClaimValidator` 는 **Tier 2**(파라미터화 필요)다.

⇒ `SecurityConfig` 를 lib 로 옮기면 lib 이 Tier 2 클래스를 참조하게 된다. **두 갈래 중 하나를 고른다**:

- **(a) 오류코드 상수만 lib 으로 승격** — `GatewayErrorCodes.TENANT_MISMATCH` 같은 상수 홀더를 lib 에 두고, 각 도메인의 `TenantClaimValidator`(아직 서비스에 있음)가 그걸 참조. `SecurityConfig` 는 lib 상수만 보면 되므로 Tier 2 를 기다리지 않는다. **권장** — 결합이 상수 하나로 최소화된다.
- **(b) `SecurityConfig` 를 step 1 에서 제외**하고 Tier 2 와 함께 step 2 로 미룸. 안전하지만 step 1 의 증명 가치가 줄어든다.

**(a) 를 우선 시도하되, 상수 승격이 도메인 코드에 예상 밖 파급을 일으키면 (b) 로 후퇴한다.** 어느 쪽이든 **결정과 근거를 PR 에 기록**한다(AC-8).

---

# Scope

## In Scope

1. **`libs/java-gateway` 모듈 신설** — `settings.gradle` include + `build.gradle`. 의존: `spring-cloud-starter-gateway`, `spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-security`, `spring-boot-starter-data-redis-reactive`, micrometer — **필요한 것만**. **서비스 모듈 의존 0**(단방향 — `shared-library-policy.md` § Dependency Rule).
2. **Tier 1 7개 클래스 이동** + 각 클래스의 **단위 테스트도 함께 lib 으로 이동**. 테스트가 클래스를 따라가지 않으면 (i) lib 이 자체 검증 없는 코드가 되고 (ii) 서비스엔 남의 클래스를 테스트하는 죽은 파일이 남는다. **둘 다 다음 사람을 속인다.**
3. **wms / scm / fan 이관** — 서비스에서 해당 클래스 삭제, `libs:java-gateway` 의존 추가, import 변경.
4. **ecommerce 는 `AllowedIssuersValidator` 만 채택**(유일한 4/4 클래스). 나머지는 손대지 않는다.
5. `platform/shared-library-policy.md` 의 **shared-library 카탈로그에 새 모듈 등록**(§ Change Rule 이 카탈로그 엔트리 갱신을 요구).

## Out of Scope

- **Tier 2 클래스 5종**(`RateLimitConfig`·`IdentityHeaderStripFilter`·`JwtHeaderEnrichmentFilter`·`TenantClaimValidator`·`OAuth2ResourceServerConfig`) — step 2.
- **ecommerce 의 `FailOpenRateLimiter`** — delegate 시그니처가 `RateLimiter<Config>` 로 일반화돼 있다(자기 override 데코레이터용). step 3.
- **단일 소비자 클래스 승격 금지**(ADR D4) — `JwksHealthProbe`(scm/fan), `AccountTypeValidationFilter`(wms), ecommerce 의 `RouteService`/`SwaggerAggregationConfig`/`GatewayMetrics`/`AccountTypeEnforcementFilter`/override 3종. **"나중에 공유할지 모르니" 는 승격 사유가 아니다** — Decision Rule 1번이 실패한다.
- **`iam`** — ADR D2.
- **동작 개선·정리·이름 변경 일체** — **순수 이동이다.** 개선하고 싶은 게 보이면 **별건 task 로 적어두고 지나간다.** 이동 PR 안의 "김에 고침" 은 D6 의 행동-불변 증명을 통째로 무효화한다.

---

# Acceptance Criteria

- [ ] AC-1 — `libs/java-gateway` 가 빌드되고 `./gradlew :libs:java-gateway:test` 가 **실제로 테스트를 실행**한다. **XML 로 `tests>0` · `skipped=0` 확인** — `BUILD SUCCESSFUL` 만으로는 0건 실행 통과(거짓 GREEN)를 구분 못 한다.
- [ ] AC-2 — **리액티브 격리**: `libs/java-gateway` 를 의존하는 모듈은 게이트웨이 4개뿐. servlet 서비스의 클래스패스에 SCG/WebFlux 가 **새로 올라가지 않음**을 `./gradlew :projects:<servlet-svc>:dependencies` 대조로 확인.
- [ ] AC-3 — **Tier 1 순수 이동 증명**: lib 의 각 클래스 코드 본문이 이관 전 각 도메인 사본과 **바이트 동일**(주석·패키지 제외 — ADR § 1.2 의 비교 절차 그대로). 결과를 PR 에 첨부.
- [ ] AC-4 — **소비자 테스트 무수정**: wms/scm/fan/ecommerce 의 기존 테스트는 **import 문 외에 한 줄도 바뀌지 않는다**. `git diff` 로 실증. **통과시키려고 고쳐야 했던 테스트가 하나라도 있으면 그것은 행동 변경이며 이 task 는 실패다**(ADR D6 — `failsOpenOnAnyReactiveError` 가 그 실물).
- [ ] AC-5 — **mutation check**: lib 의 `FailOpenRateLimiter` 에 결함 재주입(`onErrorResume` predicate 제거) → lib 테스트가 **실제로 문다**. `AllowedIssuersValidator` 도 동일(issuer 검사 무력화 → 문다). **통과하는 테스트는 증거가 아니다.**
- [ ] AC-6 — 게이트웨이 4개 서비스의 **기존 테스트 전량 GREEN**(회귀 0). CI `Build & Test (JDK 21, Linux)` + 게이트웨이 관련 Integration/E2E 레인 GREEN.
- [ ] AC-7 — 중복 제거가 **실측**된다: 삭제된 중복 클래스 파일 수 / 라인 수를 PR 에 기록.
- [ ] AC-8 — `SecurityConfig` (a)/(b) 결정과 근거가 PR 에 기록된다.

---

# Related Specs

- [`docs/adr/ADR-MONO-048`](../../docs/adr/ADR-MONO-048-shared-reactive-gateway-library.md) — **D1**(새 모듈) · **D3**(Tier 구분) · **D4**(승격 금지 목록) · **D6**(증명 의무) · **D7**(로드맵)
- [`platform/shared-library-policy.md`](../../platform/shared-library-policy.md) — § Decision Rule(4문항, ADR § 1.5 에서 통과 확인) · § Dependency Rule(단방향) · § Forbidden(도메인 로직 금지 — HARDSTOP-03) · § Change Rule(카탈로그 등록)
- [`platform/api-gateway-policy.md`](../../platform/api-gateway-policy.md) — 라이브러리가 **구현할** 정책(재정의하지 않는다)
- `libs/java-web` / `libs/java-web-servlet` — **reactive/servlet 분리 선례**. 새 모듈의 정당성 근거이자 명명 참고

# Related Contracts

없음 — 순수 내부 리팩토링. **API/event 계약 무변경**이며, 그것이 AC-4(소비자 테스트 무수정)로 강제된다.

---

# Architecture

- **왜 별도 모듈인가(ADR D1)**: `libs/java-web` 에 얹으면 WebFlux/SCG 가 **모든 servlet 서비스** 클래스패스에 올라탄다. 저장소는 이미 `java-web-servlet` 을 분리해 정확히 이 오염을 막은 선례가 있다. AC-2 가 이 성질을 지킨다.
- **`FailOpenRateLimiter` 는 이 로드맵의 상징이다.** 이 클래스의 수정이 4개 중 3개에만 전파돼 wms 가 조용히 "레이트리밋 없는 게이트웨이" 로 돌던 것이 ADR 의 논거였다. **lib 으로 옮기는 순간 그 실패 양식이 구조적으로 불가능해진다** — 이 task 의 핵심 산출이다.

---

# Edge Cases

- **`@Component` 스캔 범위 (가장 위험)** — lib 클래스에 `@Component` 가 붙어 있으면 서비스의 컴포넌트 스캔이 lib 패키지를 훑어야 잡힌다. lib 은 서비스 base package **밖**이므로 명시적 `@Bean` 등록 또는 `@ComponentScan` 확장이 필요하다. **이걸 놓치면 필터가 조용히 등록되지 않고, 단위 테스트는 통과하는데 런타임에 보안 필터가 없는 상태가 된다.** → **컨텍스트 기동 후 필터 체인에 실제 등록됐는지 단언하는 테스트를 반드시 둔다.**
- **Gradle `api` vs `implementation`** — lib 의 public 시그니처에 SCG 타입이 나오면(`GlobalFilter` 구현 등) 소비자가 그 타입을 봐야 하므로 `api` 가 필요할 수 있다. **컴파일만 통과시키려 `api` 를 남발하면 격리(AC-2)가 무너진다.**
- **`GatewayErrorHandler` 의 `ObjectMapper`** — lib 에서 빈으로 만들지 말고 **생성자 인자**로 유지(서비스가 자기 `ObjectMapper` 를 넘긴다). lib 이 빈을 강제하면 서비스의 Jackson 설정을 덮어쓸 수 있다.
- **`SecurityConfig` → `TenantClaimValidator` 상수 의존** — Goal 의 (a)/(b).
- 로컬 Windows Testcontainers 는 npipe flake → IT/E2E 는 **CI Linux 가 권위**.

---

# Failure Scenarios

| # | 시나리오 | 기대/완화 |
|---|---|---|
| 1 | 소비자 테스트를 고쳐서 통과시킴 | **행동 변경이 리팩토링 옷을 입은 것.** AC-4 가 금지. `failsOpenOnAnyReactiveError` 가 그 실물 |
| 2 | `@Component` 스캔 누락 → 필터 미등록 | **테스트는 통과하는데 런타임에 보안 필터가 없다.** 최악의 실패 모드. 필터 체인 등록 단언 테스트 필수 |
| 3 | 이동하면서 "김에" 개선/정리 | 행동-불변 증명 무효화. 별건 task 로 적고 지나간다 |
| 4 | 단일 소비자 클래스를 "미래 공유" 명목으로 승격 | Decision Rule 1번 실패. ADR D4 가 명시 배제 |
| 5 | `api` 남발로 WebFlux 가 servlet 서비스에 누출 | AC-2 가 가드 |
| 6 | 테스트를 서비스에 남긴 채 코드만 이동 | lib=미검증, 서비스=죽은 테스트. In Scope 2 가 강제 |
| 7 | lib 테스트가 통과하는 것으로 픽스를 증명 | 공허할 수 있다. **AC-5 mutation check** 가 진짜 증거 |
| 8 | `SecurityConfig` 상수 의존을 못 보고 Tier 2 를 끌어옴 | step 1 의 "변이 0" 성질이 깨진다. Goal 의 (a)/(b) 로 사전 처리 |
| 9 | `BUILD SUCCESSFUL` 을 lib 테스트 실행의 증거로 삼음 | 0건 실행 후 통과하는 거짓 GREEN. **AC-1 이 XML 확인을 강제** |

---

# Test Requirements

- **lib 단위 테스트**: 이동해 온 7개 클래스의 기존 테스트(Docker-free). **XML 로 실제 실행 확인**(AC-1).
- **필터 등록 통합 테스트**: 컨텍스트 기동 후 lib 필터가 **실제 필터 체인에 있는지** 단언 (Edge Case 1 의 방어).
- **AC-5 mutation check**: 수동이라도 수행하고 PR 본문에 결과 기록.
- **소비자 회귀**: 게이트웨이 4개의 기존 테스트 전량. IT/E2E 는 CI Linux 권위.
- **AC-2 격리 확인**: servlet 서비스 하나를 골라 `dependencies` 트리에 WebFlux/SCG 미출현 확인.

---

# Definition of Done

- [ ] `libs/java-gateway` 모듈 + `settings.gradle` include + 카탈로그 등록
- [ ] Tier 1 7개 클래스 + **테스트** 이동
- [ ] wms/scm/fan 이관 + ecommerce `AllowedIssuersValidator` 채택
- [ ] AC-3 바이트 동일 · AC-4 소비자 테스트 무수정 · AC-5 mutation check — **셋 다 PR 에 첨부**
- [ ] AC-2 리액티브 격리 확인
- [ ] `SecurityConfig` (a)/(b) 결정 기록
- [ ] CI GREEN (Build & Test + 게이트웨이 레인)
- [ ] `tasks/INDEX.md` 갱신 + **`TASK-MONO-352` spawn**

---

# Provenance

ADR-MONO-048 D7 step 1. **ACCEPTED 2026-07-11** (사용자 명시 `ADR-MONO-048 ACCEPTED`).

**왜 step 1 을 "변이 0 클래스만" 으로 잘랐는가**: 이 로드맵의 첫 미지수는 "리액티브 공유 모듈이 이 저장소의 Gradle·의존성·컨텍스트 구조에서 실제로 서는가" 다. 그 리스크를 **파라미터화라는 두 번째 미지수와 섞으면**, 뭔가 깨졌을 때 원인이 모듈 구조인지 파라미터 설계인지 판정할 수 없다. 변이 0 으로 자르면 **step 1 의 실패는 곧 모듈 구조의 실패로 특정된다.**

분석=Opus 4.8 / 구현 권장=**Opus** — 순수 이동처럼 보이지만 (i) `@Component` 스캔 경계 (ii) `api` vs `implementation` 격리 (iii) `SecurityConfig` 의 Tier 2 상수 의존, 세 지점이 기계적 이동으로는 놓치기 쉽고 그중 **(i) 은 테스트가 통과하는데 런타임 보안 필터가 사라지는** 실패 모드다.
