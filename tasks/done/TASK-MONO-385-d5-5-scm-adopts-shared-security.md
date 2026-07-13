# Task ID

TASK-MONO-385

# Title

`ADR-MONO-049` **D5-5** — scm 이 공유 보안 클래스를 채택한다. **사본 5개 삭제(31 → 26) + 인라인 구현 2개**. **§ 1.8 이 요구한 정책 결정을 내리는 단계**

# Status

done

# Owner

monorepo

# Task Tags

- shared-library
- security
- refactor
- adr-followup

---

# Dependency Markers

- **근거**: [`ADR-MONO-049`](../../docs/adr/ADR-MONO-049-framework-neutral-security-library.md) — **`ACCEPTED`, 범위 A**. § D2 · § D4 · **§ D5 단계 5** · **§ 1.8**(면제 축 — **이 task 가 그 결정을 내린다**) · § 1.9 · § 6 (V4 · V5 · V6).
- **선행 (머지됨)**: `TASK-MONO-378`(D5-1) · `TASK-MONO-382`(D5-2) · `TASK-MONO-383`(D5-3, finance) · **`TASK-MONO-384`(D5-4, erp — squash `0207aaca6`)**.
- **참조 구현**: `TASK-MONO-383`(finance) · `TASK-MONO-384`(erp). `ServiceLevelOAuth2Config` + per-service 정책 핀의 형태를 그대로 따를 것.
- **기존 단언 4개는 이 task 후에도 GREEN 이어야 한다**: `assertNoApiOnSharedLibs` · `assertClasspathNeutrality` ×2 · `assertNoServletOnReactiveEdge`.
- **후속**: D5-6 (fan, 사본 12개) — **이 task 가 랜딩한 뒤** 티켓팅. **직렬 — 병렬 착수 금지.**

---

# Goal

scm 의 servlet 서비스 3개가 공유 클래스를 채택한다. **앞의 두 단계와 달리, 이 단계는 배선만으로 끝나지 않는다 — 결정을 내려야 한다.**

## 1. 클래스 사본 5개 (31 → 26)

| 서비스 | 사본 |
|---|---|
| `procurement-service` | `AllowedIssuersValidator` · `TenantClaimValidator` · `TenantClaimEnforcer` |
| `demand-planning-service` | `TenantClaimEnforcer` **만** |
| `inventory-visibility-service` | `TenantClaimEnforcer` **만** |

## 2. 🔴 그리고 클래스가 아닌 사본이 **2개 더** 있다 — 모집단이 또 틀렸다

`demand-planning` 과 `inventory-visibility` 는 validator 를 **클래스가 아니라 `ServiceLevelOAuth2Config` 안의 인라인 람다**로 갖고 있다:

```java
public static OAuth2TokenValidator<Jwt> tenantClaimValidator(String expectedTenantId) {
    return jwt -> { ... };            // wildcard + entitled + "tenant_mismatch" — 클래스판과 같은 정책
}
private static OAuth2TokenValidator<Jwt> allowedIssuersValidator(List<String> allowed) {
    return jwt -> { ... };            // 같은 로직, 다만 에러코드가 다르다 (아래)
}
static boolean isEntitled(Jwt jwt, String domain) { ... }   // 세 번째 사본
```

**함대 전체에서 이 형태는 이 두 파일뿐이다**(전수 확인). 그래서 `^public class` 로 세는 탐지식이 **이 둘을 한 번도 보지 못했다** — § 1.7 이 scm 을 *"validator 1 / enforcer 3"* 이라 적은 이유다. **진실은 validator 클래스 1개 + 인라인 구현 2개**다.

> **이 혈통에서 카운트가 틀린 여섯 번째다** (4 → 10 → 18 → 49, D5-3 초안의 `17/17/12`, 그리고 지금 이것). **앞의 다섯 번은 *어디를 보는가* 가 좁았고, 이번엔 *무엇을 사본으로 인정하는가* 가 좁았다.** 파일 수는 여전히 5개가 맞다. 틀린 것은 *"scm 에는 손-유지되는 tenant validator 가 하나뿐"* 이라는 **함의**다.

**⇒ 이 인라인 구현 2개도 공유 클래스로 대체한다.** 남겨두면 *"정경이라 선언한 클래스의 손-유지 사본"* 이 그대로 남고, **이 ADR 이 자기 논지를 자기에게 다시 실행하는 것**이다(§ 1.6 이 D5 범위를 20 으로 넓힌 바로 그 논거).

### ⚠️ 발급자 에러코드가 실제로 다르다 — **행동 변경 지점**

| | 에러코드 |
|---|---|
| `AllowedIssuersValidator` **클래스**(공유판 포함) | `invalid_issuer` |
| scm 두 서비스의 **인라인**판 | **`invalid_token`** |

공유 클래스를 채택하면 그 두 서비스의 발급자-거부 응답 코드가 `invalid_token` → `invalid_issuer` 로 **바뀐다.** **AC-2 가 이것을 명시적으로 다뤄야 한다** — 이 코드에 반응하는 곳이 있는지(`SecurityConfig` 의 에러 매핑 · 계약 · 프런트) 확인하고, 없으면 *"관측 가능하나 소비자 없음"* 을 근거와 함께 기록하라. **조용히 바꾸지 말 것.**

---

# 🔴 결정 지점 (§ 1.8) — 이 task 의 본체

`demand-planning` 과 `inventory-visibility` 의 Enforcer 는 **`PublicPaths` 를 참조하지 않고 `/actuator/` 전체를 면제**한다:

```java
protected boolean shouldNotFilter(HttpServletRequest request) {
    return request.getRequestURI().startsWith("/actuator/");   // env · beans · heapdump · loggers 전부
}
```

형제 `procurement` 는 자기 `PublicPaths` 로 **`health` · `info` · `prometheus` 3개만** 면제한다. **그리고 그 두 서비스는 `PublicPaths` 클래스 자체가 없다**(scm 에서 `PublicPaths` 를 가진 건 procurement 뿐 — 실측).

## 결정에 필요한 사실 (전부 실측 — 추측 아님)

- **사실 A** — 두 서비스의 `SecurityConfig` 는 `/actuator/{health,info,prometheus}` **만** `permitAll` 하고 **`anyRequest().denyAll()`** 로 끝난다. ⇒ `/actuator/env` 는 Enforcer 의 면제가 상담되기 **전에** Spring Security 가 막는다. **라이브 취약점 아님.**
- **사실 B** — 그래서 *"면제돼 있지만 permit 되지 않은"* actuator 경로들은 **토큰을 들고도 도달 불가능**하다. ⇒ **면제를 3개로 좁혀도 도달 가능한 행동 변화가 없을 가능성이 높다.** **이걸 증명하는 것이 AC-3 이다.**
- **사실 C** — `inventory-visibility` 는 **`/internal/inventory-visibility/**` 를 `permitAll`** 한다. **이건 결함이 아니다** — `InternalSnapshotController` 는 `ADR-MONO-027 § D7.1` 이 문서화한 **네트워크-신뢰 경계의 tenant-agnostic 배치 읽기**이고(호출자 = 토큰 없는 `ReorderSweepScheduler`), 게이트웨이가 라우팅하지 않는다. **permitAll 이라 JWT 가 없고, Enforcer 는 `JwtAuthenticationToken` 이 아닌 요청을 그냥 통과시킨다** ⇒ **면제 술어와 무관한 경로다. 면제를 좁혀도 이 경로는 깨지지 않는다.**

## 무엇을 결정하는가

**(A) 좁힌다** — 두 서비스에도 `PublicPaths`(health · info · prometheus)를 두고 `procurement` 와 같은 술어를 넘긴다.
**(B) 유지한다** — `/actuator/` 전체 면제를 술어로 재현한다.

> **원칙적으로 (A) 는 *narrowing* = 행동 변경이다.** 그러나 사실 A·B 가 참이면 **도달 가능한 행동 변화는 0** 이고, 그때 (A) 는 *"선을 지키는 곳과 면제를 정의하는 곳을 일치시키는 것"* 이다 — § 1.8 이 지적한 그 위험(`permitAll("/actuator/**")` 한 줄이면 그 두 서비스의 테넌트 게이트가 조용히 사라진다. **그리고 그 둘은 `TenantClaimValidator` 클래스가 없어 Enforcer 가 servlet 계층의 유일한 tenant 검사다**)이 그때 사라진다.
>
> **(A) 를 권한다. 단, 사실 B 를 *증명한 뒤에*** — 증명 없이 좁히면 그건 결정이 아니라 도박이다. **증명이 실패하면 (B) 로 가고, 그 이유를 § 1.8 에 적어라.**

---

# Scope

## In Scope

1. **`build.gradle` 배선** — 3개 서비스에 `implementation project(':libs:java-security-servlet')`. `libs:java-security` 선언 여부는 **서비스별로 확인**할 것(§ 1.7 은 scm 을 다 세지 못했다). **`api` 금지**(§ D2).
2. **클래스 사본 5개 삭제** + **인라인 구현 2개 제거**(위 § 2). 참조자는 **심볼로 grep**하고 **`compileTestJava` 로 재확인**하라(D5-4 에서 확립한 절차 — grep 은 참조자를 놓칠 수 있지만 컴파일러는 못 놓친다).
3. **정경 `TenantClaimEnforcer` 를 `@Bean` 으로 명시 배선** — `@Component` 금지.
4. **면제 축 결정 이행** — 위 결정 지점. 결과를 **§ 1.8 에 기록**하라(결정과 근거 둘 다).
5. **per-service 정책 핀 테스트** ×3 — AC-4.

## Out of Scope

- **fan · wms · iam** — D5-6 ~ D5-8.
- **정경 클래스 수정** — D5-2 확정 + D5-3 § 1.9 수정. **또 고쳐야 한다고 느껴지면 멈추고 보고**하라.
- **`/internal/inventory-visibility/**` 의 `permitAll`** — `ADR-MONO-027 § D7.1` 의 의도된 설계다. **건드리지 말 것.** (이 task 의 면제 결정과 **무관**하다 — 사실 C.)
- **레거시 발급자 `iam` 제거** — `TASK-MONO-367`, 2026-08-01 게이트.
- **scm gateway-service** — 이미 lib 판을 쓴다.

---

# Acceptance Criteria

- [x] **AC-1 (사본이 사라졌다 — 클래스 **와** 인라인)** — `projects/scm-platform` 에 세 클래스 파일 **0개** (함대 **31 → 26**, `git grep` 전수 재카운트). **그리고 `ServiceLevelOAuth2Config` 안의 인라인 `tenantClaimValidator` / `allowedIssuersValidator` / `isEntitled` 도 0개** — 공유 클래스로 대체됐다. *파일 수만 세면 이 절반을 놓친다.*
- [x] **AC-2 (행동 불변 — § 6 V6 — 그리고 *한 곳은 진짜로 바뀐다*)** — scm 3개 서비스 스위트 통과. 기존 단언의 *동작*은 바뀌지 않는다(주어만 교체).
      **예외 1건: 발급자 거부 에러코드가 `invalid_token` → `invalid_issuer` 로 바뀐다**(두 서비스). **이 코드의 소비자를 전수로 찾고**(`SecurityConfig` 에러 매핑 · `specs/contracts/` · 프런트 · IT), **없으면 "관측 가능하나 소비자 없음" 을 근거와 함께 기록**하라. **소비자가 있으면 멈추고 보고하라.**
- [x] **AC-3 (면제 결정 — *증명한 뒤에* 좁힌다)** — (A) 를 택했다면: 두 서비스의 `SecurityConfig` 가 `permitAll` 하는 경로를 **전수 열거**하고, **면제에서 빠지는 actuator 경로가 전부 `denyAll` 로 이미 도달 불가능함을 보여라**(테스트로). ⇒ **도달 가능한 행동 변화 0** 을 증명한 뒤 좁힌다.
      **증명이 안 되면 (B)** 를 택하고 **§ 1.8 에 이유를 적어라.** *증명 없는 narrowing 은 결정이 아니라 도박이다.*
- [x] **AC-4 (정책 핀 = 허용 **과** 거부, 그리고 두 층의 합의)** — **서비스 3개 각각**: `tenant_id=scm` → 통과 / `"*"` → 통과 / `entitled_domains=[scm]`+`tenant_id=erp` → 통과 / `tenant_id=erp` entitled 없음 → **403** / claim 부재 → **401** / 면제경로 → 건너뜀 / **비면제 경로 → 게이트 적용** / **decoder 와 enforcer 가 같은 판정**(§ 1.9).
      **⚠️ subject 는 `ServiceLevelOAuth2Config` 에서 꺼낼 것.** 자기 builder 로 만들면 config 에서 스위치가 빠져도 초록이고 AC-5 가 연극이 된다.
- [x] **AC-5 (mutation)** — 서비스별로 `.allowSuperAdminWildcard()` / `.trustEntitledDomains()` / `.exempt(...)` 를 하나씩 빼면 **그 서비스 스위트가 RED**.
      **⚠️ mutation 이 적용됐는지 결과 읽기 전에 확인하라** — 기준을 **mutation 직전 파일**로 잡고 **사라진 줄을 출력**할 것. (D5-3 의 첫 러너는 `HEAD` 기준 diff 로 **거짓 GREEN** 을 냈다. D5-4 에서는 perl 치환이 **CRLF 때문에 0건 적용**됐는데 적용 건수를 먼저 찍어 잡았다.)
- [x] **AC-6 (기존 단언 4개 GREEN)** — artefact 수 불변(23 / 50 / 94).
- [x] **AC-7 (테스트 GREEN — XML 실측)** — `BUILD SUCCESSFUL` 을 믿지 말고 테스트 수 · skipped 를 XML 로 확인하라.

---

# 실측 결과 + 결정

## 결정: **(A) 좁힌다** — 그리고 도달 가능한 행동 변화가 **0** 임을 증명했다

**증명의 축은 필터 순서였다.** `TenantClaimEnforcer.ORDER = LOWEST_PRECEDENCE - 100`(= `Integer.MAX_VALUE - 100`)이고 Spring Security 체인은 `SecurityProperties.DEFAULT_FILTER_ORDER = -100` 에 등록된다 ⇒ **Spring Security 가 먼저 돈다.** `authorizeHttpRequests` 가 거부한 요청은 **Enforcer 에 도달조차 하지 않는다.**

⇒ **면제를 잃은 actuator 경로들**(`env` · `beans` · `loggers` · `heapdump` · `health/liveness`)은 전부 `anyRequest().denyAll()` 에 걸리므로 **애초에 필터가 본 적이 없다.** 좁히기는 **관측 불가능**하다. *(가정이 아니라 `ExemptionEqualsThePermitList` 4개 테스트가 단언한다 — 서비스당.)*

**그리고 §1.8 의 진짜 위험을 구조적으로 없앴다**: 두 서비스에 `PublicPaths` 를 만들고 **`SecurityConfig` 의 permit 리스트를 거기서 유도**하게 했다. 이제 **면제 = permit 리스트**이고, 둘이 따로 편집될 수 없다. *(§1.8 이 지적한 건 "지금 뚫려 있다" 가 아니라 "선을 지키는 곳과 면제를 정의하는 곳이 달라서, `permitAll("/actuator/**")` 한 줄이면 테넌트 게이트가 조용히 사라진다" 였다.)*

**⚠️ `PREFIXES` 는 비워 뒀다 — 실수가 아니다.** procurement 의 `PublicPaths` 는 `/actuator/health/` prefix 를 갖지만, **이 두 서비스의 `SecurityConfig` 는 `/actuator/health/liveness` 를 permit 한 적이 없다.** prefix 를 넣었으면 **리팩터링을 가장해 permit 리스트를 넓히는 것**이었다.

**사실 C 확인**: `/internal/inventory-visibility/**` 는 `permitAll` 이라 JWT 가 없고, Enforcer 는 비-`JwtAuthenticationToken` 요청을 그냥 통과시킨다 ⇒ **면제 축이 닿지 않는다.** 테스트로 못 박았다(`internalPathIsNotAnExemptionConcern`).

## AC-2 — 관측 가능한 변경 1건, 그리고 그것이 정확히 무엇인가

인라인 발급자 검증(`invalid_token`) → 공유 클래스(`invalid_issuer`). **소비자를 전수로 찾았다**:

| | before | after |
|---|---|---|
| HTTP status | **401** | **401** (불변) |
| 응답 `code` | **UNAUTHORIZED** | **UNAUTHORIZED** (불변) |
| 응답 `message` | 일반 디코드 메시지 | `iss '...' is not in the allowed list` |

**`SecurityConfig.extractOAuth2Error` 는 `invalid_token` 을 *의도적으로 건너뛴다*** (`!"invalid_token".equals(...)`) — 그래서 인라인판의 발급자 메시지는 **여태 응답에 드러난 적이 없다**. `code` 가 `TENANT_FORBIDDEN` 으로 바뀌는 분기는 `tenant_mismatch` 뿐이다.

**소비자 0건** — `specs/` · `contracts/` · 프런트 · e2e 어디에도 `invalid_issuer` 를 키로 쓰는 곳이 없다(전수 grep). **그리고 이 변경은 scm 을 erp · fan · finance 와 *일치*시킨다**(그 셋은 이미 `invalid_issuer` 를 낸다). 테스트로 못 박았다.

## 실측 수치

- **AC-1** — 클래스 사본 **31 → 26**, scm **0**. **인라인 구현 함대 전체 0** (`OAuth2TokenValidator<Jwt> tenantClaimValidator(` 전수 grep). *(1차 탐지식은 `5` 를 냈는데, 그건 **wms 의 클래스 사본**이었다 — 술어가 넓었다. 숫자를 결론으로 읽지 않고 무엇을 매치했는지 봤다.)*
- **AC-5 mutation — 9건 전부 RED** (스위치 3 × 서비스 3). 매 회차 **사라진 줄을 출력**해 적용을 먼저 확인:

  | 뺀 것 | procurement | demand-planning | inventory-visibility |
  |---|---|---|---|
  | `.allowSuperAdminWildcard()` | 2 fail | 2 fail | 2 fail |
  | `.trustEntitledDomains()` | 3 fail | 3 fail | 3 fail |
  | `.exempt(PublicPaths::isPublic)` | 2 fail | 1 fail | 1 fail |

- **AC-6** — 4/4 GREEN, artefact 불변(23 / 50 / 94).
- **AC-7** — procurement 171 · demand-planning 72 · inventory-visibility 61 · scm gateway 47 = **351 tests / 0 skipped / 0 failures / 0 errors**.

## 컴파일러가 grep 이 놓친 것을 잡았다 (D5-4 절차의 값)

**`TenantFailClosedIntegrationTest`**(demand-planning)는 클래스명이 아니라 **`ServiceLevelOAuth2Config.tenantClaimValidator(...)` 라는 메서드**를 참조한다 ⇒ 내 심볼 grep(`TenantClaim|AllowedIssuers|...`)에 **걸리지 않았다.** `compileTestJava` 가 잡았다. 그 단언 5개(scm 통과 / wms 거부 / `*` 통과 / entitled 통과 / non-entitled 거부)는 전부 새 정책 핀으로 이관했다.

## §1.9 의 세 번째 독립 확인

inventory-visibility 의 **기존** `TenantClaimEnforcerTest` 가 이미 이렇게 단언하고 있었다:

> `entitled_domains containing scm grants even when tenant_id absent` → **200**

**필터 층에서**. D5-2 의 정경 클래스(무조건 401)를 그대로 채택했다면 이 기존 테스트가 RED 가 됐을 것이다. §1.9 의 수정이 옳았다는, 세 번째이자 가장 직접적인 증거다.

---

# Related Specs

- `docs/adr/ADR-MONO-049-framework-neutral-security-library.md` — § D2 · § D4 · § D5(단계 5) · **§ 1.8**(이 task 가 결정을 내리고 기록한다) · § 1.9 · § 6
- `docs/adr/ADR-MONO-027-*.md` § D7.1 — `/internal/inventory-visibility/**` 의 근거(**건드리지 말 것**)
- `libs/java-security/.../oauth2/{AllowedIssuersValidator,TenantClaimValidator}.java`
- `libs/java-security-servlet/.../servlet/TenantClaimEnforcer.java`
- **`TASK-MONO-383` · `TASK-MONO-384`**(`tasks/done/`) — 참조 구현

# Related Contracts

**AC-2 에서 확인할 것** — 발급자 거부 에러코드가 `specs/contracts/` 에 적혀 있으면 **계약 변경**이고, 그때는 스펙을 먼저 고쳐야 한다(`CLAUDE.md` — 계약이 구현보다 먼저).

---

# Edge Cases

- **`PublicPaths` 가 두 서비스에는 없다** — (A) 를 택하면 만들어야 한다(procurement 판을 따를 것).
- **`demand-planning` · `inventory-visibility` 는 `TenantClaimValidator` **클래스**가 없다** — 대신 인라인 구현이 있다(§ 2). **"validator 가 없으니 decode 층이 없다" 고 읽지 말 것** — 있다, 형태가 다를 뿐이다.
- **`/internal/inventory-visibility/**` 는 `permitAll`** — JWT 가 없으므로 Enforcer 가 그냥 통과시킨다. **면제 술어와 무관**(사실 C).
- **패키지 배치가 서비스마다 다르다** — demand-planning · inventory-visibility 는 `adapter/inbound/web/filter/`, procurement 는 `presentation/filter/`. **경로 하드코딩 금지.**

# Failure Scenarios

- **인라인 구현 2개를 남긴다** → 파일 카운트는 26 으로 맞는데 **손-유지 사본은 그대로 남는다.** ADR 이 자기 논지를 자기에게 실행한다. Guard: AC-1 후반부.
- **에러코드 변경을 조용히 넘긴다** → 관측 가능한 행동 변경이 무근거로 랜딩한다. Guard: AC-2.
- **증명 없이 면제를 좁힌다** → 도달 가능한 회귀를 도박으로 랜딩한다. Guard: AC-3.
- **정책 핀이 자기 builder 로 subject 를 만든다** → AC-5 가 연극이 된다. Guard: AC-4 의 명시 요구.
- **`api` 로 배선한다** → § D2 위반. Guard: AC-6.

---

# Provenance

`ADR-MONO-049` § D5 단계 5. `TASK-MONO-384`(D5-4) 가 랜딩해 게이트가 열렸다.

**D5-3 과 D5-4 는 정책이 균일해서 배선 문제였다. D5-5 는 아니다** — § 1.8 이 *"D5-5 가 명시적으로 결정해야 한다"* 고 적어둔 그 단계이고, 그 결정을 뒷받침할 사실 3개(A·B·C)를 이 티켓이 실측해 두었다. **결정 자체는 코드를 쓰기 전에, 증명과 함께 내려라.**

그리고 **티켓팅 과정에서 모집단이 여섯 번째로 틀렸다는 것이 드러났다**(§ 2). 그건 우연이 아니다 — **매번 탐지식이 한 형태만 봤다.** 이번엔 `^public class` 가 인라인 람다를 못 봤다.

분석=Opus 4.8 / 구현 권장=**Opus** (결정 1건 + 관측 가능한 행동 변경 1건 + 인라인 구현 제거. 배선 난이도보다 **판단**이 요구되는 단계다).
