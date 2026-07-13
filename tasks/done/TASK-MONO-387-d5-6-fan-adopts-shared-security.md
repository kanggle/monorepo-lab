# Task ID

TASK-MONO-387

# Title

`ADR-MONO-049` **D5-6** — fan 이 공유 보안 클래스를 채택하고 **사본 12개를 지운다**(26 → 14). **스위치가 꺼진 채로 남는 첫 프로젝트**이고, `/internal/**` 면제의 하중 여부를 **증명**하는 단계

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

- **근거**: [`ADR-MONO-049`](../../docs/adr/ADR-MONO-049-framework-neutral-security-library.md) — **`ACCEPTED`, 범위 A**. § D2 · § D4 · **§ D5 단계 6** · **§ 1.8**(면제 축 — membership 결정) · **§ 1.9**(decode/filter split) · § 6.
- **선행 (머지됨)**: `TASK-MONO-378`(D5-1) · `382`(D5-2) · `383`(D5-3 finance) · `384`(D5-4 erp) · **`385`(D5-5 scm — squash `cf4da6ea5`)**.
- **참조 구현**: `TASK-MONO-385`(scm) 가 가장 가깝다 — **결정 + 증명**을 요구한 단계이기 때문이다. `ScmTenantGatePolicyTest` 의 `ExemptionEqualsThePermitList` 구조를 그대로 가져올 것.
- **기존 단언 4개는 이 task 후에도 GREEN**: `assertNoApiOnSharedLibs` · `assertClasspathNeutrality` ×2 · `assertNoServletOnReactiveEdge`.
- **후속**: D5-7 (wms, 사본 10개 — **validator 만**, Enforcer 없음) — **이 task 가 랜딩한 뒤** 티켓팅. **직렬.**

---

# Goal

fan 의 servlet 서비스 **4개**(`artist`, `community`, `membership`, `notification`)가 공유 클래스를 채택하고 **사본 12개를 지운다.**

**26 → 14.** (D5-5 가 클래스 5개 + 인라인 2개를 지워 31 → 26 이 됐다. 인라인 구현은 **함대 전체 0** — `origin/main` 실측.)

## 🔴 fan 은 `entitled_domains` 를 **끄는** 유일한 프로젝트다 — 스위치가 꺼진 채 남는 첫 사례

실측: fan 의 12개 사본 **어디에도 `isEntitled` 가 없다**(전수 grep, 0건). fan 은 entitlement plane 밖에 있고, 그 분기는 fan 에서 **dead code** 가 된다.

⇒ 배선은 **처음으로 이렇게 된다**:

```java
TenantClaimEnforcer.forTenant(expectedTenantId)
        .exempt(...)                    // 아래
        .allowSuperAdminWildcard()
        // .trustEntitledDomains() 없음 — fan 은 entitlement plane 밖이다
        .build();
```

**이것이 "모든 스위치 기본 닫힘" 설계가 처음으로 실제 값을 하는 지점이다.** D5-2 가 쓴 `entitledRefusedWhenOff` 단언이 fan 에서는 **가상의 성질이 아니라 살아 있는 정책**이 된다.

**그리고 § 1.9 의 fix 가 fan 에서 올바르게 퇴화하는지 확인하라**: `trustEntitledDomains` 가 꺼지면 `entitled` 는 항상 `false` 이므로 `(tenantId == null || blank) && !entitled` 는 **무조건 401** 로 퇴화한다 — fan 의 4개 사본이 손으로 쓴 바로 그 형태다. **AC-4 가 이것을 단언해야 한다.**

## 🔴 결정 지점 — `membership` 의 `/internal/**` 면제 (§ 1.8)

| 서비스 | 면제 |
|---|---|
| `artist` · `community` · `notification` | `PublicPaths.isPublic(request)` |
| **`membership`** | `PublicPaths.isPublic(request)` **`|| uri.startsWith("/internal/")`** |

**§ 1.8 은 이것을 "결정해야 할 것" 으로 남겼다.** 착수 전 실측한 사실:

- **membership 은 `SecurityFilterChain` 이 둘이다** — `@Order(1)` `securityMatcher("/internal/**")` (workload-identity: `JwtDecoder` + `WorkloadIdentityAuthoritiesConverter` + `hasRole("INTERNAL")`) / `@Order(2)` 엔드유저 체인.
- **내부 체인은 `JwtAuthenticationToken` 을 SecurityContext 에 넣는다** — 그런데 **workload-identity 토큰에는 `tenant_id` claim 이 없다**(필터의 주석이 그렇게 말한다).
- `TenantClaimEnforcer` 는 체인 **밖**의 서블릿 필터(`LOWEST_PRECEDENCE-100`)라 `/internal/**` 요청에도 **돈다**.
- ⇒ **면제가 없으면 `tenant_id` 없는 JWT 를 보고 401 을 낸다** ⇒ `community/HttpMembershipChecker` 의 멤버십 조회가 전부 깨진다.

**⇒ 이 면제는 하중을 받는다. 유지가 정답으로 *보인다*.**

> **그러나 "보인다" 로 남기지 말 것.** D5-5 가 세운 규율은 *증명 없는 결정은 도박이다* 였다. 여기서는 방향이 반대일 뿐 규율은 같다: **면제를 제거하는 mutation 이 membership 의 내부 테스트를 RED 로 만드는지 확인하라**(`InternalAuthIntegrationTest` · `InternalAccessControllerSliceTest` · `AccessCheckIntegrationTest`).
> - **RED → 하중 확인, 유지한다.** (예상)
> - **GREEN → 그 면제는 dead code 다.** 그러면 **제거**하고, 왜 아무도 그것을 눈치채지 못했는지 기록하라.
>
> **어느 쪽이든 mutation 이 결정하고, 산문은 결정하지 않는다.**

**면제는 `Predicate` 다** — 정경 클래스가 이 형태를 표현할 수 있다(그게 D5-2 가 클래스 참조 대신 `Predicate` 를 고른 이유다):

```java
.exempt(r -> PublicPaths.isPublic(r) || r.getRequestURI().startsWith("/internal/"))
```

---

# Scope

## In Scope

1. **`build.gradle` 배선** — 4개 서비스에 `implementation project(':libs:java-security-servlet')`. **`libs:java-security` 는 4/4 이미 선언**(실측). **`api` 금지**(§ D2).
2. **사본 12개 삭제** + 참조자 갱신. **심볼로 grep 하고 `compileTestJava` 로 재확인**하라 — **D5-5 에서 `TenantFailClosedIntegrationTest` 가 클래스명이 아니라 *메서드*를 참조해 심볼 grep 을 빠져나갔다.** grep 은 참조자를 놓칠 수 있어도 컴파일러는 못 놓친다.
3. **정경 `TenantClaimEnforcer` 를 `@Bean` 으로 명시 배선** — `@Component` 금지. **`.trustEntitledDomains()` 를 켜지 말 것**(fan 의 정책).
4. **membership 면제 결정 이행** — 위 결정 지점. 결과를 **§ 1.8 에 기록**.
5. **per-service 정책 핀** ×4 — AC-3.

## Out of Scope

- **wms · iam** — D5-7 · D5-8.
- **정경 클래스 수정** — 또 고쳐야 한다고 느껴지면 **멈추고 보고**하라.
- **fan gateway-service** — 이미 lib 판을 쓴다(사본 없음).
- **membership 의 내부 체인 자체** — workload-identity 설계는 이 task 밖이다. **면제만** 다룬다.
- **레거시 발급자 `iam` 제거** — `TASK-MONO-367`, 2026-08-01 게이트.

---

# Acceptance Criteria

- [x] **AC-1 (사본이 사라졌다)** — `projects/fan-platform` 에 세 클래스 파일 **0개**. 함대 **26 → 14**. **`git grep` 전수 재카운트.** **인라인 구현도 0 인지 함께 확인**(fan 은 실측상 없지만, **모집단은 이 혈통에서 여섯 번 틀렸다** — 확인이 비용보다 싸다). **탐지식이 낸 숫자를 결론으로 읽지 말고 무엇을 매치했는지 볼 것**(D5-5 의 1차 스윕은 `5` 를 냈는데 그건 wms 의 클래스 사본이었다).
- [x] **AC-2 (행동 불변 — § 6 V6)** — fan 4개 서비스 스위트 통과. 기존 단언의 *동작*은 바뀌지 않는다(주어만 교체). **`AllowedIssuersValidator` 는 fan 에서 이미 클래스 판이라 D5-5 의 에러코드 변경 같은 건 없다** — 그래도 **확인은 하라**(fan 판이 `invalid_issuer` 를 내는지 실측: 그렇다면 무변경).
- [x] **AC-3 (정책 핀 = 허용 **과** 거부, 그리고 두 층의 합의)** — **서비스 4개 각각**: `tenant_id=fan-platform` → 통과 / `"*"` → 통과 / **`entitled_domains=[fan-platform]` + 다른 `tenant_id` → 403** ← **fan 에서는 거부가 정답이다** / `tenant_id` 부재 → **401** / 면제경로 → 건너뜀 / 비면제 → 적용 / **decoder ↔ enforcer 합의**(§ 1.9).
      **⚠️ subject 는 `ServiceLevelOAuth2Config` 에서 꺼낼 것** — 자기 builder 로 만들면 config 에서 스위치가 빠져도 초록이고 AC-5 가 연극이 된다.
- [x] **AC-4 (`trustEntitledDomains` 가 꺼진 채 남는다 — 그리고 § 1.9 가 올바르게 퇴화한다)** — ① `entitled_domains` 만 있고 `tenant_id` 가 맞지 않는 토큰은 **decoder 와 enforcer 양쪽에서 거부**된다 ② `tenant_id` 부재 + `entitled_domains=[fan-platform]` → **401**(entitled 가 꺼져 있으므로 무조건 401 로 퇴화 — fan 의 옛 사본과 동일). **이 두 단언이 "기본 닫힘" 설계가 실제로 값을 하는 지점이다.**
- [x] **AC-5 (mutation — 배선이 진짜인가)** — 각 서비스 config 에서 **`.allowSuperAdminWildcard()` 제거 → RED**, **`.exempt(...)` 제거 → RED**. **그리고 반대 방향도**: **`.trustEntitledDomains()` 를 *추가*하면 AC-3/AC-4 의 거부 단언이 RED** 여야 한다 — *스위치를 켜는 mutation 이 잡히지 않으면, fan 의 "꺼짐" 은 테스트되지 않는 것이다*(MONO-355 의 교훈: 켠 상태만 기록하는 스위트는 스위치가 사라져도 초록이다. **여기선 반대로 꺼진 상태를 기록해야 한다**).
      **⚠️ mutation 적용 여부를 결과 읽기 전에 확인**(기준=직전 파일, 사라진/추가된 줄 출력). D5-3 의 첫 러너는 `HEAD` 기준 diff 로 **거짓 GREEN**, D5-4 의 perl 치환은 **CRLF 때문에 0건 적용**됐다.
- [x] **AC-6 (membership `/internal/**` 면제 — mutation 이 결정한다)** — 면제에서 `/internal/` 절을 **제거한 상태로** membership 스위트를 돌려라.
      **RED → 하중 확인. 유지하고, 무엇이 RED 였는지 기록하라.** **GREEN → dead code. 제거하고, 왜 아무도 몰랐는지 기록하라.** **산문으로 결정하지 말 것.**
- [x] **AC-7 (기존 단언 4개 GREEN)** — artefact 수 불변(23 / 50 / 94).
- [x] **AC-8 (테스트 GREEN — XML 실측)** — `BUILD SUCCESSFUL` 을 믿지 말고 테스트 수 · skipped 를 XML 로 확인하라.

---

# 실측 결과 + 결정

## AC-6 결정: **면제는 하중을 받는다 — 유지한다.** 그리고 **아무도 그걸 테스트하고 있지 않았다**

`/internal/` 절만 정확히 제거하고 membership 스위트를 돌렸다(의미 변경은 그 한 줄뿐임을 `diff -w` 로 확인, 가드가 `.allowSuperAdminWildcard()` 2개·`.trustEntitledDomains()` 0개를 먼저 검증).

**결과: `GRADLE_EXIT=1`, RED 그룹 = `/internal/** is exempt — and it has to be` 1건, 오탐 0.** ⇒ **하중 확인. 유지.**

**🔴 그런데 RED 가 난 건 *내가 방금 쓴 테스트뿐이었다*. 그게 이 단계의 진짜 발견이다.**

membership 의 기존 테스트 중 **어느 것도 이 필터를 체인에 갖고 있지 않다**:

| 테스트 | 필터를 보는가 |
|---|---|
| `InternalAuthIntegrationTest` | ❌ Spring 애너테이션 **없음** — 순수 단위 테스트 |
| `AccessCheckIntegrationTest` | ❌ 동일 |
| `InternalAccessControllerSliceTest` | ❌ `@WebMvcTest` + `SliceTestSecurityConfig` 인데 **그 config 에 enforcer 빈이 없다** |

⇒ **이 task 전까지, 프로덕션에서 `/internal/` 절을 지우면 모든 내부 호출이 401 이 나는데 membership 스위트는 전부 초록이었다.** 면제는 **무방비**였다. `TASK-MONO-355`(wms 의 와일드카드 *거부* 에 커버리지 0) · `TASK-MONO-373`(어느 잡에서도 안 도는 스펙)과 **같은 클래스**다.

**증명의 사슬**(추론이 아니라 각 고리가 실측):
1. `internalJwtDecoder` 의 Javadoc 이 스스로 말한다 — *"does NOT pin tenant_id"*.
2. `@Order(1)` 내부 체인은 그래도 `JwtAuthenticationToken` 을 컨텍스트에 넣는다(`WorkloadIdentityAuthoritiesConverter`).
3. Enforcer 는 `tenant_id` 없는 `JwtAuthenticationToken` 을 **401** 한다(정책 핀이 단언).
4. Enforcer 는 체인 **밖**의 서블릿 필터(`LOWEST_PRECEDENCE-100`)라 `/internal/**` 에도 돈다(D5-5 가 확립).

⇒ 면제가 없으면 **`community/HttpMembershipChecker` 가 죽는다.**

**면제는 테넌트 게이트의 구멍이 아니다** — `/internal/**` 는 엔드유저 라우트가 아니고, 그것을 지키는 체인은 `hasRole("INTERNAL")` 을 요구한다. **테넌트 claim 은 그 표면 앞을 막고 선 것이 아니다.**

## AC-4 · AC-5: **스위치가 꺼진 채 남는 첫 사례 — 양방향으로 못 박았다**

fan 12개 사본 전수: `isEntitled` **0건**. ⇒ `.trustEntitledDomains()` 를 **부르지 않는다**.

**mutation 8건, 양방향 전부 RED:**

| | artist | community | membership | notification |
|---|---|---|---|---|
| `.allowSuperAdminWildcard()` **제거** | 2 fail | 2 fail | 2 fail | 2 fail |
| **`.trustEntitledDomains()` 추가** ⬅ | **3 fail** | **3 fail** | **3 fail** | **3 fail** |
| `.exempt(PublicPaths::isPublic)` 제거 | 1 fail | 1 fail | — | 1 fail |

**스위치를 *켜는* mutation 이 잡힌다는 것이 핵심이다.** *켠 상태로만 테스트되는 스위치는 테스트되지 않은 것이고, fan 에서는 테스트되지 않던 쪽이 "꺼짐" 이었다.* **기본 닫힘이라 누락은 게이트를 좁혀 시끄럽지만, 추가는 넓히고 넓어지는 방향은 조용하다.**

**§1.9 의 퇴화도 단언했다**: entitled 가 꺼지면 `(tenantId==null||blank) && !entitled` 가 **무조건 401** 로 퇴화한다 — fan 4개 사본이 손으로 쓴 그 형태다.

## AC-2: 에러코드 변경 **없음** (scm 과 다르다)

fan 의 `AllowedIssuersValidator` 는 4/4 이미 **`invalid_issuer`** 를 낸다(공유 클래스와 동일 — 실측). D5-5 에서 scm 이 겪은 `invalid_token → invalid_issuer` 같은 관측 가능한 변경이 **여기엔 없다.** 정책 핀이 코드를 단언한다.

## 실측 수치

- **AC-1** — 클래스 사본 **26 → 14**, fan **0**, 인라인 **0**.
- **AC-7** — 빌드 단언 4/4 GREEN, artefact 불변(23 / 50 / 94).
- **AC-8** — artist 106 · community 99 · membership 80 · notification 57 · fan gateway 39 = **381 tests / 0 skipped / 0 failures / 0 errors**.

## 함정 셋 (전부 가드가 먼저 잡았다)

1. **`grep -c` 가 주석을 셌다** — `.trustEntitledDomains()` 가 3건 나왔는데 **전부 Javadoc·주석**이었다. `^\s*\.trustEntitledDomains\(\)` 로 실제 **호출**만 세니 **0**. *(`grep -c` 가 Javadoc 을 센다 — MONO-375 와 같은 함정.)*
2. **첫 AC-6 mutation 이 오염됐다** — perl 이 4줄을 먹어 **`.allowSuperAdminWildcard()` 까지 지웠다.** RED 가 `/internal` 때문인지 wildcard 때문인지 **구분 불가** ⇒ 결과 폐기, 줄 번호 기반으로 재실행. **제거된 줄 목록을 출력했기에 알았다.**
3. **가드 술어 자체가 틀렸다** — `.allowSuperAdminWildcard()` 는 **validator 와 enforcer 양쪽에 있어 2가 정상**인데 1을 기대했다 ⇒ 정상 mutation 을 "오염" 으로 오판하고 중단. **틀린 방향이 안전한 쪽(false negative)이라 사고는 없었다.** *가드는 실패 모드에 맞춰야 하고, 가드도 틀린다.*

또한 perl 정규식이 매치 실패 시 **치환문을 파일 맨 앞에 붙여** 소스를 깨뜨린 사건이 있었다 — 가드가 결과를 읽기 전에 잡았고 파일은 복원됐다.

---

# Related Specs

- `docs/adr/ADR-MONO-049-framework-neutral-security-library.md` — § D2 · § D4 · § D5(단계 6) · **§ 1.8** · **§ 1.9** · **§ 1.10** · § 6
- `libs/java-security/.../oauth2/{AllowedIssuersValidator,TenantClaimValidator}.java`
- `libs/java-security-servlet/.../servlet/TenantClaimEnforcer.java` — builder, 전 스위치 기본 닫힘
- **`TASK-MONO-385`**(`tasks/done/`) — **결정 + 증명을 요구한 단계.** `ExemptionEqualsThePermitList` 구조를 가져올 것.
- `TASK-MONO-383` · `TASK-MONO-384` — 배선 참조 구현

# Related Contracts

없다 — 행동 불변이 목표다. **AC-2 에서 `AllowedIssuersValidator` 의 에러코드를 실측해 확인할 것**(D5-5 에서 scm 은 실제로 바뀌었다).

---

# Edge Cases

- **`PublicPaths` 는 서비스마다 다른 클래스다** — 4개가 각자의 것을 넘긴다.
- **`membership` 의 `PublicPaths` 는 `/actuator/health/` prefix 를 갖는다**(실측) — scm 의 두 서비스와 달리 **`SecurityConfig` 가 이미 `PublicPaths` 에서 permit 리스트를 유도한다**. **fan 은 §1.8 의 "두 곳에서 유지" 문제가 애초에 없다.** 건드리지 말 것.
- **`community` 의 사본 주석이 scm 의 넓은 면제를 명시적으로 거부한다** — *"A blanket `/actuator/` prefix would bypass the tenant gate for endpoints that may be added later (`/actuator/env`, `/actuator/heapdump`, …); we want a fail-closed posture there."* **fan 은 이 축에서 이미 옳다.** 그 판단을 삭제하지 말고 정경 배선의 주석/테스트로 **보존**하라.
- **`notification` 은 `TenantClaimEnforcer` 테스트가 없다**(실측 — 나머지 3개는 있다). AC-3 이 4개 전부에 존재해야 한다.

# Failure Scenarios

- **`.trustEntitledDomains()` 를 습관적으로 켠다** → **fan 의 게이트가 넓어진다**(entitlement 를 신뢰하지 않던 서비스가 신뢰하게 된다). **기본 닫힘이라 누락은 좁아지지만, 추가는 넓어진다 — 그리고 넓어지는 방향은 조용하다.** Guard: AC-4 · AC-5 의 역방향 mutation.
- **membership 면제를 산문으로 판단한다** → 하중을 받는 면제를 지우면 community→membership 이 전부 깨지고, dead code 를 남기면 §1.8 이 그대로 남는다. Guard: AC-6.
- **정책 핀이 자기 builder 로 subject 를 만든다** → AC-5 가 연극이 된다. Guard: AC-3 의 명시 요구.
- **import 로 grep 해서 참조자를 놓친다** → D5-1 · D5-3 · D5-4 · **D5-5** 에서 네 번 걸렸다. Guard: 심볼 grep + `compileTestJava`.

---

# Provenance

`ADR-MONO-049` § D5 단계 6. `TASK-MONO-385`(D5-5) 가 랜딩해 게이트가 열렸다.

**D5-3 · D5-4 는 배선이었고, D5-5 는 결정이었다. D5-6 은 둘 다다** — membership 의 면제는 결정이고(AC-6), fan 전체는 **스위치를 꺼진 채로 두는 첫 배선**이다(AC-4).

**그리고 fan 은 이 ADR 의 논지에 반례처럼 보이는 곳이다** — `community` 의 사본은 scm 이 저지른 실수를 **주석으로 명시적으로 거부**하고 있다. 손-유지 사본이 항상 틀린다는 뜻이 아니다. **아무도 대조하지 않으면 갈라진다는 뜻이고, fan 이 옳았다는 사실 자체를 아무도 몰랐다는 것이 요점이다.**

분석=Opus 4.8 / 구현 권장=**Opus** (스위치를 켜는 방향의 실수가 **조용히 게이트를 넓힌다**. 그리고 결정 1건이 mutation 에 달려 있다).
