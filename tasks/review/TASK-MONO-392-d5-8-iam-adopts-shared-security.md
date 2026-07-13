# Task ID

TASK-MONO-392

# Title

`ADR-MONO-049` **D5-8 (마지막 단계)** — iam 의 servlet 2개가 공유 보안 클래스를 채택하고 **사본 4개를 지운다**(4 → **0**). **두 스위치가 모두 꺼진 유일한 프로젝트**이고, **슬라이스 테스트가 정책을 두 번째로 쓰고 있는** 곳이다

# Status

review

# Owner

monorepo

# Task Tags

- shared-library
- security
- refactor
- adr-followup

---

# Dependency Markers

- **근거**: [`ADR-MONO-049`](../../docs/adr/ADR-MONO-049-framework-neutral-security-library.md) — **`ACCEPTED`, 범위 A**. § D2 · § D4 · **§ D5 단계 8(마지막)** · **§ D6**(게이트웨이 제외) · § 1.7 · **§ 1.11** · § 6.
- **선행 (머지됨)**: `378`(D5-1) · `382`(D5-2) · `383`(D5-3) · `384`(D5-4) · `385`(D5-5) · `387`(D5-6) · **`390`(D5-7 wms — squash `c981e7fd2`)**.
- **참조 구현**: **`TASK-MONO-390`(wms)** 가 가장 가깝다 — validator 만, Enforcer 없음, 정책 핀 구조 동일. **`TASK-MONO-387`(fan)** 은 **슬라이스 테스트 위임** 패턴의 레퍼런스다(아래 § 실측 3).
- **기존 단언 4개는 이 task 후에도 GREEN**: `assertNoApiOnSharedLibs` · `assertClasspathNeutrality` ×2 · `assertNoServletOnReactiveEdge`. artefact **23 / 50 / 94 불변**.
- **후속**: **없다. D5 가 끝난다.** § 7 로드맵의 마지막 행이고, 사본은 **0** 이 된다.

---

# Goal

iam 의 servlet 2개(`community-service` · `membership-service`)가 손-유지 사본 대신
`libs/java-security` 의 `AllowedIssuersValidator` · `TenantClaimValidator` 를 쓰게 한다.

**사본 4개(main) + 4개(test) 삭제. 남는 사본 4 → 0.** `ADR-MONO-049` D5 완결.

---

# 실측 (2026-07-13, `origin/main` `c981e7fd2` — 착수 시 **다시 세라**)

## 실측 1 — 모집단 4 / 2서비스, Enforcer 0, 인라인 람다 0

| 서비스 | `AllowedIssuersValidator` | `TenantClaimValidator` | `TenantClaimEnforcer` | test 사본 |
|---|---|---|---|---|
| `community` | ✅ `infrastructure/security/` | ✅ | **없음** | 2 |
| `membership` | ✅ `infrastructure/security/` | ✅ | **없음** | 2 |

**두 서비스 모두 `libs:java-security` 를 이미 선언한다** — 새 의존 줄이 필요 없다(wms 의 `admin` 과 달리).
`TenantClaimEnforcer` 는 **함대 전체에서 이미 0** 이다(§ 1.11). iam 도 가진 적이 없다. **추가하지 말 것.**
인라인 람다 validator **0건**(§ 1.10 의 함정 재확인).

## 실측 2 — 정책은 **두 스위치 모두 OFF**. 함대에서 가장 엄격하다

두 사본 모두 `"*"` 참조 **0건**, `isEntitled`/`entitled_domains` **0건**.

⇒ 정경 체인은 **스위치를 하나도 부르지 않는다**:

```java
TenantClaimValidator.forTenant(requiredTenantId).build()   // 와일드카드 ✗, entitlement ✗
```

**⚡ 그래서 D5-8 은 mutation 이 전부 "켜는" 방향이다.** wms(D5-7)는 스위치 하나가 꺼져 있었고, iam 은 **둘 다** 꺼져 있다.
빌더가 기본 닫힘이므로 **누락은 여기서 아예 불가능하고**(부를 게 없다), 유일한 실패 모드는 **습관적으로 켜서 게이트를 넓히는 것**이다. **넓어지는 방향은 조용하다.**

## 실측 3 — 🔴 **슬라이스 테스트가 정책을 두 번째로 쓴다** (이 단계의 본체)

`community/support/SliceTestSecurityConfig` · `membership/support/SliceTestSecurityConfig` 는 **자기 손으로 체인을 세운다**:

```java
OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
        new JwtTimestampValidator(),
        new AllowedIssuersValidator(List.of(SAS_ISSUER, LEGACY_ISSUER)),
        new TenantClaimValidator(DEFAULT_TENANT_ID));       // ← 프로덕션이 아니다
```

**이것이 `ADR-MONO-049` 가 없애려는 바로 그것이다** — 정책이 적히는 두 번째 장소. **프로덕션 게이트가 바뀌어도 이 슬라이스 테스트들은 초록으로 남는다.**
`MONO-355`(wms 와일드카드 거부 커버리지 0) · `MONO-387`(membership `/internal/**` 면제 무방비)와 **같은 클래스**다.

**해법은 `TenantGateTestConfig` 를 만드는 것이 아니다**(그건 세 번째 사본이다). `TASK-MONO-387` 이 fan 의 notification 에서 한 대로 — **프로덕션 config 의 빈 메서드에 위임하라.**

`CommunityIntegrationTestBase` · `MembershipJwtTestSupport` 도 이 심볼을 참조한다(실측). **`compileTestJava` 로 확인할 것.**

## 실측 4 — 에러코드 불변

| 축 | iam 사본 | 공유 클래스 | |
|---|---|---|---|
| tenant | `tenant_mismatch` | `tenant_mismatch` | **동일** |
| issuer | `invalid_issuer` | `invalid_issuer` | **동일** |

**wms 와 같은 이유로 사소하지 않다**: 두 `SecurityConfig` 가 **에러코드로 403 `TENANT_FORBIDDEN` 을 분기**한다(실측). 바뀌면 **403 이 401 로 조용히 퇴화**한다.
**그리고 슬라이스 테스트 config 도 같은 분기를 갖는다** — 실측 3 을 고치면 이 분기도 프로덕션 하나로 수렴한다.

## 실측 5 — ⚠️ **같은 테넌트를 지키는 게이트 둘이 갈라져 있다** (관찰 — 이 task 가 고치지 않는다)

`iam-platform` 의 `community`·`membership` 은 **`tenant_id = fan-platform` 을 핀한다**(`application.yml`: `OIDC_REQUIRED_TENANT_ID:fan-platform`).
그런데 **`fan-platform` 프로젝트에도 같은 이름의 `community-service`·`membership-service` 가 있다**(D5-6 이 방금 이관한 그것들).

**두 게이트의 정책이 다르다:**

| | 와일드카드 | entitlement |
|---|---|---|
| `fan-platform/community` (D5-6) | **✅ 허용** | ✗ |
| `iam-platform/community` (이 task) | **❌ 거부** | ✗ |

**같은 테넌트 슬러그를 지키는 두 게이트가 SUPER_ADMIN 와일드카드에 대해 반대로 답한다.** (`iam-platform` = 옛 `global-account-platform` 개명 — `TASK-MONO-179`. 포크 이전 원본으로 보인다.)

**§ 1.7 의 표는 두 프로젝트를 각각 적었을 뿐 *연결하지 않았다*.** 어느 쪽이 옳은지는 **행동에 관한 질문이고, 추출은 행동을 바꾸지 않는다**(§ 1.11 이 wms 에서 같은 규칙을 적용했다).

⇒ **이 task 는 iam 의 OFF/OFF 를 보존하고, 이 발산을 ADR 에 기록한다.** 화해는 **별도 티켓의 일**이며, 그 티켓이 먼저 답해야 할 질문은 *"iam-platform 의 community/membership 은 살아 있는 서비스인가, 아니면 fan 이 자기 사본을 가진 뒤 남은 잔재인가"* 이다. **먼저 그것부터 물어라 — 죽은 모듈의 정책을 화해시키는 것은 무의미하다.**

---

# Scope

**포함:**

1. `community` · `membership` 의 `OAuth2ResourceServerConfig` 가 공유 클래스를 쓰도록 배선.
   `TenantClaimValidator.forTenant(id).build()` — **스위치를 하나도 부르지 않는다.**
2. main 사본 **4개** + test 사본 **4개** 삭제.
3. **슬라이스 테스트 config 2개가 프로덕션 config 의 빈 메서드에 위임**하도록 수정(실측 3). **정책을 다시 쓰는 테스트 config 를 만들지 말 것.**
4. 서비스별 **정책 핀**(`IamTenantGatePolicyTest`) — subject 는 **프로덕션 config 에서** 온다.
5. ADR § 7 로드맵 D5-8 ✅, 사본 **4 → 0**. **D5 완결 선언.**

**제외:**

- **`TenantClaimEnforcer` 추가 금지** — iam 도 가진 적 없다(§ 1.11 과 동일 규칙).
- **iam `gateway-service`** — § D6. 손대지 말 것.
- **와일드카드/entitlement 를 켜지 말 것** — 실측 5 의 발산은 **관찰이지 이 task 의 수정 대상이 아니다.**
- `libs/` 변경 — 필요 없다. **필요해지면 그것이 신호다: 멈추고 왜인지 물어라.**

---

# Acceptance Criteria

- **AC-1 (배선 + 참조자)** — 2개 서비스가 공유 클래스를 쓴다. 사본 8개(main 4 + test 4) 삭제. **심볼 grep + `compileTestJava` 둘 다**. 같은 패키지 참조는 import 줄이 없다 — D5-1·3·4·5 에서 **네 번** 걸렸고 D5-7 에서 처음으로 grep 이 완전했다. **완전한지 아는 유일한 방법은 컴파일러다.**
- **AC-2 (에러코드 회귀)** — `tenant_mismatch` · `invalid_issuer` 불변. **cross-tenant → 403 `TENANT_FORBIDDEN`** 유지를 테스트로 확인.
- **AC-3 (정책 핀)** — subject 를 **프로덕션 config 의 빈 메서드**에서 가져온다. 자기 손으로 체인을 세우는 테스트는 **연극이다**(MONO-355).
- **AC-4 (거부를 단언한다)** — **와일드카드 `"*"` → 거부** 와 **`entitled_domains` 만으로는 열리지 않음** 을 **둘 다** 단언한다. **iam 은 둘 다 거부하는 유일한 프로젝트다.**
- **AC-5 (mutation — 전부 켜는 방향)** — `.allowSuperAdminWildcard()` **추가** → 2개 서비스 전부 RED. `.trustEntitledDomains()` **추가** → 2개 서비스 전부 RED.
  **iam 에서는 이것이 유일하게 의미 있는 mutation 이다** — 부를 스위치가 없으므로 "누락" 이라는 실패 모드가 존재하지 않는다.
  **적용 건수를 먼저 출력하고, 바뀐 줄을 눈으로 확인한 뒤 결과를 읽어라.** 가드는 **pre-mutation 파일**에 앵커하라(HEAD 아님 — mutation 이 지우는 줄은 이 task 가 방금 추가한 줄이다).
- **AC-6 (슬라이스 테스트가 프로덕션을 본다)** — 슬라이스 config 를 프로덕션에 위임시킨 뒤, **프로덕션 게이트를 mutate 하면 슬라이스 테스트도 RED 가 되는지** 확인한다. **지금은 초록으로 남는다 — 그것이 이 AC 의 존재 이유다.** (mutation 이 슬라이스까지 물지 않으면 위임이 성립하지 않은 것이다.)
- **AC-7 (빌드 단언)** — 4개 GREEN, artefact **23 / 50 / 94 불변**.
- **AC-8 (ADR — D5 완결)** — § 7 D5-8 ✅ + 총계 **49 → 0**. **§ 1.12 신설**: 실측 5 의 게이트 발산(같은 테넌트, 반대 정책)을 기록하고 **후속 질문(iam 의 두 서비스는 살아 있는가)** 을 남긴다. § 5 Consequences 에 **D5 완결** 을 적는다.

---

# Related Specs

- `docs/adr/ADR-MONO-049-framework-neutral-security-library.md` — § D5(단계 8) · § D6 · § 1.7 · **§ 1.11** · § 5 · § 7
- **`TASK-MONO-390`**(`tasks/done/`) — validator-only 배선 + 정책 핀 구조의 직전 레퍼런스
- **`TASK-MONO-387`**(`tasks/done/`) — **슬라이스 테스트를 프로덕션에 위임**시킨 레퍼런스(AC-6)
- `libs/java-security/.../oauth2/{AllowedIssuersValidator,TenantClaimValidator}.java`

# Related Contracts

**없다 — 행동 불변이 목표다.** 다만 `tenant_mismatch` → 403 `TENANT_FORBIDDEN` 은 두 `SecurityConfig` 가 실제로 의존하는 계약이다. AC-2 가 지킨다.

---

# Edge Cases

- **두 서비스 모두 `libs:java-security` 를 이미 선언한다** — build.gradle 을 건드릴 일이 없다. 건드렸다면 뭔가 잘못 짚은 것이다.
- **`iam-platform` 의 community/membership 은 `fan-platform` 을 핀한다** — 이름만 보고 `iam` 테넌트일 거라 가정하지 말 것(실측 5).
- **`fan-platform` 에도 같은 이름의 서비스가 있다** — **경로를 반드시 확인하고 편집하라.** 파일명이 같다.
- **`CommunityIntegrationTestBase` · `MembershipJwtTestSupport` 가 심볼을 참조한다** — 삭제 후 `compileTestJava` 로 확인.
- **iam `gateway-service` 는 이 ADR 범위 밖**이다(§ D6). 손대지 말 것.

# Failure Scenarios

- **습관적으로 스위치를 켠다** → **함대에서 가장 엄격한 게이트가 조용히 넓어진다.** iam 은 **둘 다 거부하는 유일한 프로젝트**다. Guard: AC-4 + AC-5.
- **슬라이스 테스트용 `TenantGateTestConfig` 를 만든다** → 정책이 **세 번째** 장소에 적힌다. **이 ADR 이 존재하는 이유가 그것이다.** Guard: Scope 3 + AC-6.
- **슬라이스 config 를 그냥 두고 넘어간다** → 사본은 지웠는데 **정책의 두 번째 사본은 테스트에 남는다.** 프로덕션 게이트가 바뀌어도 슬라이스는 초록. Guard: AC-6.
- **실측 5 의 발산을 "고친다"** → 추출이 행동 변경으로 오염된다. Guard: Scope 제외 + AC-8(기록만).
- **에러코드가 바뀌어 403 이 401 로 퇴화한다** → Guard: AC-2.

---

# Provenance

`ADR-MONO-049` § D5 **단계 8 — 마지막**. `TASK-MONO-390`(D5-7, wms) 이 랜딩해 게이트가 열렸다.

**이 단계가 끝나면 사본은 0 이 되고, 49개에서 시작한 D5 가 완결된다.** ADR 이 처음 세었을 때 그 숫자는 **4** 였고, 세 번의 정정을 거쳐 **49** 가 됐다(§ 1.6).

**그리고 이 마지막 단계에서 ADR 은 자기 논지를 한 번 더 만난다.** 사본 4개를 지우는 일은 쉽다. **어려운 것은 슬라이스 테스트 두 개가 정책을 다시 쓰고 있다는 사실**이고 — 그건 클래스 사본이 아니라서 **어떤 카운트에도 잡힌 적이 없다.** § 1.10 이 인라인 람다에 대해 한 말이 여기서도 참이다: **카운트는 술어만큼만 정직하다.**

분석=Opus 4.8 / 구현 권장=**Opus** (배선은 사소하지만 **AC-6 이 판단을 요구한다** — 위임이 성립했는지는 mutation 이 슬라이스까지 무는지로만 알 수 있고, 여기서 손쉬운 오답은 정책을 세 번째로 복제하는 것이다).

---

# 구현 결과 (2026-07-13, Opus 4.8) — **D5 완결. 사본 49 → 0.**

## 파일 4개는 쉬운 절반이었다

사본 8개(main 4 + test 4) 삭제 ⇒ **함대 사본 0**. 테스트 **237 / 0 failures / 0 errors / 0 skipped**(community 141 · membership 96), `compileTestJava` **7개 iam 모듈 전부 GREEN**, 빌드 단언 4/4(artefact 23/50/94 불변).

## ⚡ AC-6 — 정책의 두 번째·세 번째 집은 **테스트 안에** 있었고, 그 대가를 실측했다

`SliceTestSecurityConfig` ×2 **와 통합 베이스**(`CommunityIntegrationTestBase` · `MembershipJwtTestSupport`) ×2 가 **각자 validator 체인을 손으로 세우고 있었다.** 티켓은 슬라이스 2곳만 짚었다 — **통합 베이스 2곳은 *참조자*로만 적었지 *재진술자*인 줄 몰랐다.** 서비스당 2곳, 총 4곳.

> **디코더의 *키*를 바꾸는 건 정당하다**(슬라이스에 auth-service 가 없으니 JWKS 대신 로컬 키로 검증). **정책을 바꾸는 건 아니다.** 그리고 이 파일들은 **한 호흡에 둘 다** 했다 — 그래서 아무도 눈치채지 못했다. **디코더를 만질 정당한 이유가 부당한 변경을 같이 태우고 들어왔다.**

**"프로덕션 게이트가 바뀌어도 초록으로 남는다" 는 주장은 쉽다. 그래서 재봤다** — 프로덕션 `jwtTokenValidator()` 에 **모든 토큰을 거부하는 canary validator** 를 심고 전 스위트 실행:

| 프로덕션 테넌트 게이트가 **전부 거부** | 테스트 쪽 실패 |
|---|---|
| **D5-8 이전** (테스트가 자기 체인을 세움) | **0건 — 50개 스위트 전부 초록** |
| **D5-8 이후** (프로덕션에 위임) | **36건** (community 22 · membership 14) |

**프로덕션이 존재하는 모든 토큰을 거부하는데 50개 스위트가 전부 초록이었다.** 그것이 "정책의 두 번째 사본" 의 실제 의미다. `MONO-355`(wms 와일드카드 거부 커버리지 0) · `MONO-387`(membership `/internal/**` 무방비)와 **같은 문장을 벌었다**: **관측할 수 없는 성질은 아무도 지키고 있지 않은 성질이다.**

**`TenantGateTestConfig` 를 만들지 않았다** — 그건 정책의 **세 번째** 사본이고, **테스트 헬퍼야말로 세 번째 사본이 숨기 좋은 자리다**(중복 제거처럼 보이면서 정반대를 한다). 테스트는 이제 `OAuth2ResourceServerConfig#jwtTokenValidator()` 를 부르고 **키 교체만** 남긴다.

## AC-4 / AC-5 — 두 거부를 모두 단언, mutation 은 전부 켜는 방향

iam 은 **두 스위치 다 끄는 유일한 프로젝트**다. 빌더가 기본 닫힘이라 **"빠뜨림" 이라는 실패 모드가 도달 불가능하다** — 틀리는 유일한 길은 습관적으로 **켜는** 것이고 그건 게이트를 **넓힌다**.

| mutation | 결과 |
|---|---|
| `.allowSuperAdminWildcard()` **추가** | 2/2 서비스 RED (각 1건) |
| `.trustEntitledDomains()` **추가** | 2/2 서비스 RED (각 2건) |

**넓히는 mutation 은 슬라이스 테스트를 깨뜨리지 못한다** — 유효한 토큰만 쓰는 테스트는 게이트가 넓어져도 그대로 통과하기 때문이다. **그래서 AC-6 은 넓힘이 아니라 오염(canary)으로 증명해야 했다.** 이걸 늦게 깨달았고, 첫 AC-6 시도는 슬라이스 0건을 내고도 "위임 실패" 가 아니라 **"내 mutation 설계가 틀렸다"** 는 뜻이었다.

## 🔴 AC-1 — **컴파일러가 이 티켓의 실측을 반박했다** (그리고 그 티켓은 "grep 이 드디어 완전했다" 고 적은 직후였다)

이 티켓의 § 실측 1 은 *"두 서비스 모두 `libs:java-security` 를 이미 선언한다 — 새 의존 줄이 필요 없다"* 고 단언했다. **틀렸다.**

**`membership` 은 `testImplementation` 으로 선언한다.** 내 grep 은 **문자열** `libs:java-security` 를 세어 둘 다 `1` 을 냈고, **configuration 을 보지 않았다** — 그런데 그게 유일하게 중요한 부분이다(`testImplementation` 은 프로덕션 컴파일 클래스패스에 닿지 않는다). 첫 컴파일이 `package com.example.security.oauth2 does not exist` 로 죽었다. ⇒ `implementation` 으로 승격.

> **`grep -c 'java-security'` 는 의존성에 관한 술어가 아니다. 텍스트에 관한 술어다.** `MONO-390` 이 *"grep 이 처음으로 완전했다"* 고 적은 바로 다음 단계에서, **자기 신뢰성을 방금 선언한 그 측정을 컴파일러가 반박했다.**

## 🔴 그리고 나는 mutation 을 커밋했다 — 이 글을 쓴 사람이

**첫 푸시가 CI RED 였다.** 실패한 테스트 2개 = **`EntitlementIsRefused`** — *이 task 가 "iam 은 entitlement 스위치를 거부한다" 를 증명하려고 방금 쓴 그 단언들.*

**원인**: mutation 실행 **두 개가 같은 워크트리에서 동시에** 돌았다(하나는 백그라운드, 하나는 그게 끝나기 전에 포그라운드로 띄웠다). 한 프로세스의 **복원**이 다른 프로세스의 **적용**과 경합했고 **적용이 이겼다.** `.trustEntitledDomains()` 가 두 프로덕션 config 에 남았다 — **`no .trustEntitledDomains()` 라고 적힌 주석 바로 밑에.** 그대로 커밋됐다.

**잔여물 검사는 `.premut` 파일과 canary validator 를 찾았다. 둘 다 0이었고 "깨끗함" 이라고 보고했다. 스위치 자체는 한 번도 보지 않았다.**

> **가드가 자기가 막으려던 실패 모드를 정확히 못 봤다** — 이 시리즈에서 **세 번째**로 가드의 *대상*이 아니라 *술어*가 틀렸다(D5-6 은 나쁜 카운트로 멀쩡한 mutation 을 중단시켰고, D5-7 은 게이트웨이 하나만큼 어긋났다). 세 번 다 처방은 같다: **지키려는 성질을 직접 단언하라, 대리 지표 말고.** 그 검사는 한 줄이다 — *iam 프로덕션 config 의 스위치 호출 = 0* — 이제 그게 돈다.
>
> **잡아낸 것은 이 task 가 쓴 정책 핀이다.** 이 커밋에서 태어난 테스트가 **자기 커밋을 CI 에서 RED 로 만들었다.** § 1.12 의 논지가 § 1.12 를 쓴 사람에게 그대로 집행됐다: ***스위트의 가치는 통과한다는 데 있지 않다. 보고 있다는 데 있다.*** 보고 있었다.
>
> **그리고 "로컬에서 초록이었다" 는 애초에 할 만한 주장이 아니었다** — **내가 테스트한 산출물과 내가 커밋한 산출물이 갈라졌고, 그중 진짜는 하나뿐이었다.** 검증은 이제 컴파일 결과가 아니라 **staged blob** 을 본다.

**운영 교훈**: 같은 워크트리에서 mutation 스크립트를 **동시에 두 개 돌리지 말 것.** (로컬 IT 가 느려 백그라운드로 돌린 것이 발단이었다.)

## ADR

**§ 1.12 신설**(테스트 안의 두 번째 집 + canary 실측 + 마지막 오측정 + **위 사건**) · **§ 6 에 V7 추가**(*"테스트가 프로덕션이 배선한 게이트를 보고 있는가"* — **V5 의 그림자이고, V5 보다 먼저 물어야 하는 질문**) · **§ 5.1 = D5 완결 선언** · § 7 로드맵 D5-8 ✅ **49 → 0**.

**§ 5.1 의 결론**: 중복은 *증상*이었다. 병은 **이 성질들 중 어느 것도 감시받고 있지 않았다는 것**이다. 아무도 안 보는 사본과 아무도 안 보는 정경 클래스는 **같은 방식으로 실패한다** — 통합이 도움이 되는 이유는 **감시할 가치가 있는 자리를 하나로 만들기 때문**이지 사본을 줄이기 때문이 아니다.
