# Task ID

TASK-MONO-390

# Title

`ADR-MONO-049` **D5-7** — wms 의 servlet 5개가 공유 보안 클래스를 채택하고 **사본 10개를 지운다**(14 → 4). **Enforcer 가 없는 첫 프로젝트**이고, **와일드카드를 유일하게 거부하는** 프로젝트다 — 그래서 mutation 은 **넓히는 방향**으로만 의미가 있다

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

- **근거**: [`ADR-MONO-049`](../../docs/adr/ADR-MONO-049-framework-neutral-security-library.md) — **`ACCEPTED`, 범위 A**. § D2 · § D4 · **§ D5 단계 7** · § D6(게이트웨이 제외) · § 6.
- **선행 (머지됨)**: `TASK-MONO-378`(D5-1) · `382`(D5-2) · `383`(D5-3 finance) · `384`(D5-4 erp) · `385`(D5-5 scm) · **`387`(D5-6 fan — squash `bbabdbfa4`)**.
- **참조 배선은 ADR 이 아니라 코드에 이미 있다**: **`wms/gateway-service` 가 이미 공유 `TenantClaimValidator` 를 쓴다**(게이트웨이 혈통 수렴, `ADR-MONO-048` D7). 아래 § 실측 3 참조.
- **기존 단언 4개는 이 task 후에도 GREEN**: `assertNoApiOnSharedLibs` · `assertClasspathNeutrality` ×2 · `assertNoServletOnReactiveEdge`. artefact 수 **23 / 50 / 94 불변**.
- **후속**: D5-8 (iam, 사본 4개 — community · membership) — **이 task 가 랜딩한 뒤** 티켓팅. **직렬**(전부 `libs/` + `settings.gradle` 을 건드린다).

---

# Goal

wms 의 servlet 5개(`admin` · `inbound` · `outbound` · `inventory` · `master`)가 손-유지 사본 대신
`libs/java-security` 의 `AllowedIssuersValidator` · `TenantClaimValidator` 를 쓰게 한다.
**사본 10개(main) + 9개(test) 삭제. 남는 사본 14 → 4**(iam 만 남는다).

**행동은 불변이어야 한다.** 아래 § 실측이 그것이 가능함을 이미 보였다.

---

# 실측 (착수 전 수행 — 숫자를 물려받지 말 것)

> 이 시리즈에서 모집단은 **여섯 번** 틀렸다. 아래는 `2026-07-13` `origin/main` `bbabdbfa4` 기준 **재측정 결과**이며,
> 착수 시 **다시 세라**. 특히 § 1.10 의 함정(**인라인 람다는 클래스 grep 에 안 걸린다**)을 반드시 재확인할 것.

## 실측 1 — 모집단은 10개 / 5서비스이고, **Enforcer 는 0개다**

| 서비스 | `AllowedIssuersValidator` | `TenantClaimValidator` | `TenantClaimEnforcer` | 배선 위치 |
|---|---|---|---|---|
| `admin` | ✅ `infra/security/` | ✅ `infra/security/` | **없음** | `infra/security/OAuth2ResourceServerConfig` |
| `inbound` | ✅ `config/security/` | ✅ `config/security/` | **없음** | `config/security/OAuth2ResourceServerConfig` |
| `outbound` | ✅ `config/security/` | ✅ `config/security/` | **없음** | `config/security/OAuth2ResourceServerConfig` |
| `inventory` | ✅ `config/security/` | ✅ `config/security/` | **없음** | `config/security/OAuth2ResourceServerConfig` |
| `master` | ✅ `config/security/` | ✅ `config/security/` | **없음** | `config/security/OAuth2ResourceServerConfig` |

**main 사본 10 / test 사본 9**(`admin` 은 `AllowedIssuersValidatorTest` 가 없다 — 실측).
5개 전부 `validators.add(new AllowedIssuersValidator(...)); validators.add(new TenantClaimValidator(...));` 로 **동일하게** 배선돼 있다.

**⚡ wms 는 `TenantClaimEnforcer` 가 하나도 없다 — D5 에서 처음이다.** 결과:

- **`.exempt(...)` 축이 없다** → `PublicPaths` · permit 리스트 · 필터 순서 증명(§ 1.8 / D5-5 의 `ExemptionEqualsThePermitList`)이 **이 단계엔 존재하지 않는다.**
- **§ 1.9 의 "decode-pass / filter-block split" 이 wms 에선 구조적으로 불가능하다.** 층이 하나뿐이기 때문이다. D5-3 이 잡은 그 결함은 여기서 발생할 수 없다.
- **wms 에 Enforcer 를 *추가*하지 말 것.** 그것은 추출이 아니라 **행동 변경**이다(게이트웨이 config 의 주석이 같은 원칙을 말한다: *"changing the answer is a behaviour change and does not belong in an extraction"*). 필요 여부는 별도 판단이며, 이 task 는 **있는 것을 옮길 뿐이다.** 관찰 결과는 ADR § 1.8 에 기록하라.

**범위 밖 2개 (실측으로 확인):**

- **`notification-service` — OAuth2 리소스 서버가 아예 없다.** `jwtDecoder` · `oauth2ResourceServer` · `OAuth2TokenValidator` **전부 0건**. 게이트가 없으니 옮길 것도 없다. *(이것이 갭인지 여부는 이 task 의 질문이 아니다. 다만 기록은 남겨라.)*
- **`gateway-service` — 이미 공유 클래스를 쓴다.** § D6 대로 D5 범위 밖이다.

## 실측 2 — wms 의 정책은 **와일드카드 OFF · entitled ON** 이고, 5개가 동일하다

사본의 주석이 스스로 말한다: *"wms keeps strict legacy equality (no `"*"` wildcard)"*.
**wms 는 scm · fan · finance · erp 가 받아들이는 SUPER_ADMIN 와일드카드를 유일하게 거부한다.** 의도된 선택이며 `ADR-MONO-048` § D5 가 명시 보존한다.

## 실측 3 — 정경 체인은 **이미 프로덕션에서 돌고 있다**

`wms/gateway-service/config/OAuth2ResourceServerConfig#tenantGate()`:

```java
public TenantClaimValidator tenantGate() {
    return TenantClaimValidator.forTenant(requiredTenantId)
            .trustEntitledDomains()
            .build();                 // .allowSuperAdminWildcard() 없음 — 의도적
}
```

servlet 5개는 **이 체인을 그대로** 쓰면 된다. 추측이 아니다 — **wms 의 엣지가 이미 이 조합으로 돌고 있고**, 그 정책 핀(`gateway/security/TenantClaimValidatorTest`)은 **프로덕션 배선에서 subject 를 주입받는다**(MONO-355 교리). 그대로 가져올 레퍼런스다.

## 실측 4 — **에러코드가 바뀌지 않는다** (D5-5 에서 scm 은 실제로 바뀌었다)

| 축 | wms 사본 | 공유 클래스 | |
|---|---|---|---|
| tenant | `tenant_mismatch` | `tenant_mismatch` | **동일** |
| issuer | `invalid_issuer` | `invalid_issuer` | **동일** |

**이것은 사소하지 않다.** wms 의 5개 `SecurityConfig` 는 `AuthenticationEntryPoint` 에서 **에러코드로 403 `TENANT_FORBIDDEN` 을 분기**한다(실측: `admin` · `inbound` · `inventory` · `master` · `outbound` 전부). 코드가 바뀌면 **403 이 401 로 퇴화**하고 `master/OidcAuthIntegrationTest` 가 그것을 잡는다. **바뀌지 않는다는 것을 실측했으므로 AC-2 는 회귀 확인이지 마이그레이션이 아니다.**

## 실측 5 — 분기 등가성

공유 validator 를 `allowWildcard=false, trustEntitledDomains=true, acceptAnyWellFormedTenant=false` 로 세우면
wms 사본과 **분기별로 동일**하다(성공 3분기 · 실패 2분기 · 실패 메시지 문자열까지).

## 실측 6 — 의존 선언은 **`admin` 하나만 없다**

`inbound` · `outbound` · `inventory` · `master` 는 이미 `implementation project(':libs:java-security')` 를 선언한다.
**`admin-service/build.gradle` 에만 추가하면 된다.** (ADR § 1.7 의 메모와 일치 — 그래도 착수 시 재확인할 것.)

---

# Scope

**포함:**

1. `admin` · `inbound` · `outbound` · `inventory` · `master` 의 `OAuth2ResourceServerConfig` 가 공유 클래스를 쓰도록 배선.
   `TenantClaimValidator` 는 **정경 빌더 체인**(실측 3)으로. `.allowSuperAdminWildcard()` **호출 금지**.
2. main 사본 **10개** 삭제.
3. test 사본 **9개** 삭제 → 서비스별 **정책 핀**(`WmsTenantGatePolicyTest`)으로 대체. subject 는 **프로덕션 config 에서** 가져온다.
4. `admin-service/build.gradle` 에 `implementation project(':libs:java-security')` 추가.

**제외:**

- **`TenantClaimEnforcer` 추가 금지** — 행동 변경이다(실측 1).
- **`gateway-service`** — 이미 공유. **`notification-service`** — 게이트 없음.
- **와일드카드 정책 변경 금지** — wms 의 거부는 결정이다.
- `libs/` 자체 변경 — 이 단계엔 필요 없다(공유 클래스가 이미 wms 의 정책을 표현한다). **필요해지면 그것이 신호다: 멈추고 왜인지 물어라.**

---

# Acceptance Criteria

- **AC-1 (배선)** — 5개 서비스가 공유 `AllowedIssuersValidator` · `TenantClaimValidator` 를 쓴다. main 사본 10개 · test 사본 9개 삭제. **`grep`(심볼) + `compileTestJava` 둘 다** 로 참조자 부재를 확인한다 — *같은 패키지 참조는 import 줄이 없어서 grep 이 놓친다*(D5-1 · D5-3 · D5-4 · D5-5 에서 **네 번** 걸렸다).
- **AC-2 (에러코드 회귀)** — `tenant_mismatch` · `invalid_issuer` 가 **불변**임을 테스트로 확인한다. 특히 **cross-tenant → 403 `TENANT_FORBIDDEN`** 이 유지되는지 `master/OidcAuthIntegrationTest` 로 확인한다(실측 4).
- **AC-3 (정책 핀)** — 서비스마다 정책 핀이 **프로덕션 config 의 빈 메서드에서** subject 를 가져온다. 자기 손으로 `forTenant(...)` 체인을 세우는 테스트는 **연극이다**(MONO-355).
- **AC-4 (거부를 단언한다)** — **와일드카드 `tenant_id="*"` → 거부** 를 5개 전부에서 단언한다. **`TASK-MONO-355` 가 찾아낸, 거부 쪽 커버리지가 0이던 바로 그 게이트다.**
- **AC-5 (mutation — 넓히는 방향)** — **`.allowSuperAdminWildcard()` 를 *추가*하면** 5개 서비스의 스위트가 **전부 RED** 여야 한다.
  **wms 에서 의미 있는 mutation 은 이 방향뿐이다**: 빌더는 기본 닫힘이라 스위치 *누락*은 게이트를 좁혀 시끄럽지만, **와일드카드 *추가*는 게이트를 넓히고 넓어지는 방향은 조용하다.**
  `.trustEntitledDomains()` 를 **제거**하는 mutation 도 RED 여야 한다(entitled 수용 분기를 지키는 쪽).
  **mutation 은 적용 건수를 먼저 출력하고, 제거/추가된 줄을 눈으로 확인한 뒤에 결과를 읽어라** — 이 시리즈에서 mutation 이 조용히 0회 적용된 적이 있고(CRLF), 4줄을 먹어 결과를 오염시킨 적도 있다.
- **AC-6 (빌드 단언)** — `assertNoApiOnSharedLibs` · `assertClasspathNeutrality` ×2 · `assertNoServletOnReactiveEdge` GREEN. artefact **23 / 50 / 94 불변**.
- **AC-7 (ADR)** — § 1.8 에 **"wms 에는 Enforcer 가 없다"** 는 관찰과 그 결정(추가하지 않는다 + 왜)을 기록. § 7 로드맵 D5-7 ✅. 사본 **14 → 4**.

---

# Related Specs

- `docs/adr/ADR-MONO-049-framework-neutral-security-library.md` — § D2 · § D4 · § D5(단계 7) · § D6 · § 1.7 · **§ 1.8** · § 6
- **`projects/wms-platform/apps/gateway-service/.../config/OAuth2ResourceServerConfig.java`** — **정경 체인의 살아있는 레퍼런스**(실측 3)
- **`projects/wms-platform/apps/gateway-service/src/test/.../security/TenantClaimValidatorTest.java`** — 정책 핀의 살아있는 레퍼런스(AC-3)
- `projects/wms-platform/specs/integration/iam-integration.md` — `tenant_mismatch` → 403 `TENANT_FORBIDDEN` 계약
- `libs/java-security/.../oauth2/{AllowedIssuersValidator,TenantClaimValidator}.java`
- `TASK-MONO-387`(fan) · `385`(scm) — 배선 참조 구현

# Related Contracts

**없다 — 행동 불변이 목표다.** 다만 `iam-integration.md` 의 `tenant_mismatch` → 403 `TENANT_FORBIDDEN` 은 **계약이다.** AC-2 가 그것을 지킨다.

---

# Edge Cases

- **`admin` 은 사본이 `infra/security/` 에 있다**(나머지 4개는 `config/security/`). 경로 가정 금지.
- **`admin` 은 `AllowedIssuersValidatorTest` 가 없다** — test 사본은 10이 아니라 **9** 다.
- **`admin` 만 `libs:java-security` 미선언** — 나머지 4개는 이미 있다.
- **`master` 의 사본만 `ERROR_CODE_TENANT_MISMATCH` 를 public 상수로 노출**하고 Javadoc 이 길다. 다른 곳에서 참조하는지 **심볼 grep 으로** 확인할 것.
- **`gateway` 의 테스트는 `com.example.security.oauth2.TenantClaimValidator` 를 import 한다** — 삭제 대상 아님. **파일명이 같다고 지우지 말 것.**
- **인라인 람다 validator 가 없다는 것을 재확인하라** — § 1.10 에서 scm 이 그 함정에 걸렸다. `2026-07-13` 실측은 0건이었다.

# Failure Scenarios

- **습관적으로 `.allowSuperAdminWildcard()` 를 켠다** → **wms 의 엣지가 플랫폼 운영자에게 열린다.** wms 는 이것을 **의도적으로 거부하는 유일한 프로젝트**다. Guard: AC-4 + AC-5(넓히는 방향 mutation).
- **`.trustEntitledDomains()` 를 빠뜨린다** → entitlement 로 들어오던 토큰이 전부 거부된다(게이트가 좁아진다 — 시끄럽지만 회귀다). Guard: AC-5 역방향.
- **에러코드가 바뀌어 403 이 401 로 퇴화한다** → `iam-integration.md` **계약 위반**. 실측상 바뀌지 않지만 **확인 없이 넘기지 말 것**. Guard: AC-2.
- **"Enforcer 가 없네" 하고 추가한다** → 추출이 행동 변경으로 오염된다. Guard: Scope 제외 + AC-7 의 명시 기록.
- **import 로 grep 해서 참조자를 놓친다** → 네 번 걸렸다. Guard: 심볼 grep + `compileTestJava`.

---

# Provenance

`ADR-MONO-049` § D5 단계 7. `TASK-MONO-387`(D5-6, fan) 이 랜딩해 게이트가 열렸다.

**이 단계는 시리즈에서 가장 잘 규정돼 있다** — 그리고 그것은 우연이 아니다. **wms 의 게이트웨이가 이미 이 정확한 공유 체인으로 돌고 있기 때문이다.** 즉 D5-7 은 "새 배선을 설계"하는 게 아니라 **같은 프로젝트 안에서 이미 증명된 배선을 servlet 쪽으로 맞추는** 일이다. 등가성은 추론이 아니라 실측이다(실측 5).

**동시에 이 단계는 방향이 뒤집힌 첫 단계다.** 다른 프로젝트에서 위험은 *스위치를 빠뜨려* 게이트가 좁아지는 쪽이었다. wms 에서 위험은 **스위치를 켜서 게이트가 넓어지는 쪽**이다 — 그리고 `TASK-MONO-355` 가 이미 말했듯, **거부 쪽엔 커버리지가 없었다.** 넓어지는 방향은 조용하다. AC-4 와 AC-5 가 그 침묵을 깨는 장치다.

분석=Opus 4.8 / 구현 권장=**Sonnet** (배선은 정경 레퍼런스가 코드에 이미 있고, 결정 사항이 없다 — `TenantClaimEnforcer` 를 **추가하지 않는다**는 결정은 이 티켓이 이미 내렸다. **단, AC-5 의 mutation 은 반드시 실행하고 적용 여부를 눈으로 확인할 것.**)

---

# 구현 결과 (2026-07-13, Opus 4.8)

## 모집단 — 티켓의 숫자를 물려받지 않고 다시 셌고, 맞았다

main **10** / test **9** / 인라인 람다 **0**. 사본 **14 → 4**(iam 만 남는다). 게이트웨이의 `TenantClaimValidatorTest` 는 **공유 클래스를 import 하므로 삭제하지 않았다** — 파일명이 같다고 지웠으면 정책 핀 하나를 잃을 뻔했다.

## AC-1 — 컴파일러가 grep 을 다시 이겼다 (그리고 이번엔 아무것도 놓치지 않았다)

심볼 grep 이 **숨은 참조자 6곳**을 찾아냈다: `SecurityConfig` **5개 전부**가 `TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH` 로 **403 을 분기**하고, **`admin` 은 `TenantClaimValidator.isEntitled(...)` 를 권한 변환기에서 직접 호출**한다. 여기에 `outbound` 의 `SecurityContextCallerScopeProvider` Javadoc `{@link}` 가 삭제될 클래스를 가리키고 있었다(공유 FQN 으로 교정).

`compileTestJava` **7개 모듈 전부 GREEN**(servlet 5 + gateway + notification). 이번엔 컴파일러가 추가로 잡은 것이 없다 — **grep 이 처음으로 완전했고, 그것을 아는 유일한 방법이 컴파일러였다.**

## AC-2 — 에러코드 불변, 실측대로

`tenant_mismatch` · `invalid_issuer` **둘 다 공유 클래스와 동일**. 빈 allowlist → `IllegalArgumentException` 도 공유 클래스가 그대로 던진다(**기동 실패 = fail-fast**; 게이트가 열린 채 뜨지 않는다). 정책 핀이 셋 다 못 박았다.

## AC-3/AC-4 — 없던 단언을 만들었다

`WmsTenantGatePolicyTest` ×5, **75 tests / 0 skipped / 0 failures**. subject 는 **프로덕션 `jwtTokenValidator()`** 에서 온다(자기 손으로 세운 체인이 아니다 — MONO-355).

**지워진 사본들의 테스트는 와일드카드 거부를 단 한 번도 단언하지 않았다.** tenant 일치 · 크로스테넌트 거부 · entitlement 수용 · malformed fail-closed 는 다 있었는데, **wms 를 함대의 나머지와 구별하는 유일한 성질만 아무도 지키지 않고 있었다.** `TheWildcardIsRefused` 가 그 빈자리다.

## AC-5 — mutation 양방향, 5/5 서비스 전부 RED

| mutation | 방향 | 결과 |
|---|---|---|
| **`.allowSuperAdminWildcard()` 추가** | **넓힘 (조용한 쪽)** | **5/5 서비스 RED, 각 1건** — 오탐 0 |
| `.trustEntitledDomains()` 제거 | 좁힘 | **5/5 서비스 RED, 각 3건** |

**적용 건수를 먼저 출력하고 제거/추가된 줄을 눈으로 확인한 뒤 결과를 읽었다.** 가드는 pre-mutation 파일에 앵커했다(HEAD 가 아니라 — mutation 이 지우는 줄은 *이 task 가 방금 추가한* 줄이라 HEAD diff 에는 `-` 로 나타나지 않는다).

**⚠️ 그리고 이번에도 가드 술어가 틀렸다 — 그런데 게이팅하지 않아서 살았다.** `.trustEntitledDomains()` 호출 수를 5로 기대했는데 실제는 **6**이었다(servlet 5 + **게이트웨이 1**). D5-6 에서는 똑같은 종류의 오차가 **멀쩡한 mutation 을 중단시켰다.** 이번엔 그 값을 **가드가 아니라 출력**으로 썼기에 사고가 없었다. *가드도 틀린다 — 그러니 틀렸을 때 무엇이 죽는지가 중요하다.*

## AC-6 — 빌드 단언 4/4 GREEN

artefact **23 / 50 / 94 불변**.

## AC-7 — ADR

**§ 1.11 신설**: wms 에 Enforcer 가 없다는 관찰 + **추가하지 않는다는 결정과 그 이유**(추출이 아니라 행동 변경) + **mutation 방향 역전** + 정경 체인이 이미 프로덕션에 있다는 사실. § 7 로드맵 D5-7 ✅, 사본 14 → 4.

## ⚠️ 환경 — 로컬 Testcontainers 는 권위가 아니다 (그리고 내가 그것을 악화시켰다)

`LotRepositoryImplTest` 등 IT 가 `ContainerLaunchException` 으로 실패한다. 원인은 **호스트 포화**(사용자의 fed-e2e 데모 스택 56 컨테이너 가동 중) — 코드와 무관하고, **CI Linux 가 권위**다. 데모 스택은 건드리지 않았다.

**그리고 내가 그 IT 실행을 kill 하면서 Gradle 빌드 캐시에 불완전한 `compileJava` 산출물이 저장됐다.** 그 뒤 `inventory-service` 가 *"package com.wms.inventory.application.port.out does not exist"* 로 컴파일 실패했는데 — **소스는 멀쩡했다.** `clean` 을 해도 캐시가 깨진 산출물을 **`FROM-CACHE` 로 다시 복원**했다. `--no-build-cache --rerun-tasks` 로 해소.

**진단 중에 또 한 번 잘림을 부재로 읽었다**: `ls ... | head` 가 목록을 자르는 바람에 `domain/model/masterref` 가 **없다고 결론**낼 뻔했다. 실제로는 있었다. **`FROM-CACHE` 라는 한 단어가 진짜 증거였고, 그것은 콘솔의 성공/실패 줄이 아니라 태스크 상태 줄에 있었다.**
