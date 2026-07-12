# Task ID

TASK-MONO-383

# Title

`ADR-MONO-049` **D5-3** — finance 가 공유 보안 클래스를 채택하고 **사본 6개를 지운다**. **처음으로 실제로 무언가가 삭제되는 단계**

# Status

ready

# Owner

monorepo

# Task Tags

- shared-library
- security
- refactor
- adr-followup

---

# Dependency Markers

- **근거**: [`ADR-MONO-049`](../../docs/adr/ADR-MONO-049-framework-neutral-security-library.md) — **`ACCEPTED`, 범위 A**. § D2 · § D4 · **§ D5 단계 3** · **§ 1.8**(정책 축) · § 6 (V4 · V5 · V6).
- **선행 (머지됨)**: `TASK-MONO-378`(D5-1 — 두 validator 가 `libs/java-security` 로) · `TASK-MONO-382`(D5-2 — `libs/java-security-servlet` + 정경 `TenantClaimEnforcer`, squash `32bea7d99`).
- **기존 단언 4개는 이 task 후에도 GREEN 이어야 한다**: `assertNoApiOnSharedLibs` · `assertClasspathNeutrality`(java-security) · `assertClasspathNeutrality`(java-security-servlet) · `assertNoServletOnReactiveEdge`.
- **후속**: D5-4 (erp, 사본 12개) — **이 task 가 랜딩한 뒤** 티켓팅. **직렬 — 병렬 착수 금지.**

---

# Goal

D5-1 과 D5-2 는 **집을 지었다.** 이 단계가 **처음으로 이사한다.**

finance 의 두 서비스(`account-service`, `ledger-service`)가 공유 클래스를 채택하고, **자기 사본 6개를 지운다**:

| 서비스 | 사본 |
|---|---|
| `account-service` | `AllowedIssuersValidator` · `TenantClaimValidator` · `TenantClaimEnforcer` |
| `ledger-service` | `AllowedIssuersValidator` · `TenantClaimValidator` · `TenantClaimEnforcer` |

**49 → 43.**

## finance 의 정책 (§ 1.8 실측 — 추측하지 말 것)

| 축 | finance |
|---|---|
| wildcard `"*"` | **허용** |
| `entitled_domains` | **신뢰** |
| 면제 경로 | **`PublicPaths.isPublic`** (프로젝트 고유 클래스) |

⇒ 배선은 이렇게 된다:

```java
TenantClaimEnforcer.forTenant(expectedTenantId)
        .exempt(PublicPaths::isPublic)
        .allowSuperAdminWildcard()
        .trustEntitledDomains()
        .build();
```

**`TenantClaimEnforcer` 의 모든 스위치는 기본 닫힘이다.** 셋 다 **명시적으로 켜야** 현재 동작이 재현된다. **하나라도 빠뜨리면 게이트가 좁아지고**(요청이 403 으로 죽고), **더 넣으면 넓어진다.** § 6 V6 이 그것을 잡아야 한다.

## `ledger-service` 는 이 세 클래스에 대해 테스트가 **0개**다

`ADR-MONO-049` § 1.3 이 지목한 그 서비스다 — **3개 클래스 전부 보유, 테스트 0.** `account-service` 는 `TenantClaimValidatorTest` 와 `TenantClaimEnforcerTest` 를 갖는다.

**⇒ 이 task 는 `ledger-service` 에 처음으로 그 커버리지를 준다** — 공유 모듈의 스위트를 **상속**함으로써(클래스 계약: fail-closed, 필수 claim, 발급자 거부). **그게 § 1.3 을 닫는 방식이고, D5 가 존재하는 이유의 절반이다.**

---

# Scope

## In Scope

1. **`build.gradle` 배선** — 두 서비스에 `implementation project(':libs:java-security-servlet')`. `libs:java-security` 는 **이미 둘 다 선언하고 있다**(§ 1.7 실측 — 20개 중 19개가 그렇다). **`api` 금지**(§ D2).
2. **사본 6개 삭제** + 그것을 참조하는 배선 클래스의 import 갱신:
   `ServiceLevelOAuth2Config` · `ActorContextJwtAuthenticationConverter` · `SecurityErrorHandler` (서비스당 3개).
3. **정경 `TenantClaimEnforcer` 를 빈으로 배선** — 위 builder 형태. **`@Component` 로 자동 스캔되던 사본과 달리, 공유 클래스는 `@Bean` 으로 명시 배선한다.** 그게 정책이 **코드에 보이게** 하는 유일한 방법이다.
4. **per-domain 테스트를 정책 핀으로 재작성** — 아래 AC-3.

## Out of Scope

- **erp · fan · scm · wms · iam** — D5-4 ~ D5-8.
- **정경 클래스 수정** — D5-2 에서 확정. 여기서 파라미터가 부족하다고 느껴지면 **그건 finance 의 정책이 § 1.8 의 축 3개로 표현이 안 된다는 뜻**이고, 그러면 **멈추고 보고**할 것(§ 1.8 이 틀렸다는 증거다).
- **`ServiceLevelOAuth2Config` 의 발급자 allowlist 에서 레거시 `iam` 제거** — `TASK-MONO-367` 의 일이고 **2026-08-01 게이트**다. 건드리지 말 것.

---

# Acceptance Criteria

- [ ] **AC-1 (사본이 실제로 사라졌다)** — `projects/finance-platform` 에 `AllowedIssuersValidator` · `TenantClaimValidator` · `TenantClaimEnforcer` 파일 **0개**. 함대 총계 **49 → 43** (17 / 17 / 12 · **gateway 는 이미 lib 판을 쓰므로 무관**).
- [ ] **AC-2 (행동 불변 — § 6 V6)** — finance 의 두 서비스 스위트가 통과한다. **`account-service` 의 기존 두 테스트가 단언하던 *동작*은 한 줄도 바뀌면 안 된다** — 주어(subject)만 사본에서 공유 클래스로 바뀐다. *단언을 고쳐야 했다면 채택이 정책을 바꾼 것이다.*
- [ ] **AC-3 (per-domain 테스트 = 정책 핀, 허용 **과** 거부 둘 다)** — 두 서비스 각각, **finance 의 게이트가 무엇을 허용하고 무엇을 거부하는지** 단언한다:
  - `tenant_id=finance` → **통과**
  - `tenant_id="*"` → **통과** (wildcard 켬)
  - `entitled_domains=[finance]` + `tenant_id=erp` → **통과** (entitled 켬)
  - `tenant_id=erp`, entitled 없음 → **403 TENANT_FORBIDDEN**
  - `tenant_id` 부재 → **401**
  - `PublicPaths` 면제 경로 → **게이트 건너뜀** / 비면제 경로 → **게이트 적용**
  **거부 쪽이 절반이다.** 허용만 기록하는 스위트는 스위치가 사라져도 초록이다(MONO-355).
- [ ] **AC-4 (`ledger-service` 가 처음으로 커버리지를 얻는다)** — § 1.3 이 지목한 **테스트 0** 상태가 해소됐다. AC-3 의 단언이 `ledger-service` 에도 존재한다.
- [ ] **AC-5 (mutation — 배선이 진짜인가)** — builder 에서 **`.allowSuperAdminWildcard()` 를 빼면 finance 스위트가 RED** 여야 한다. **`.trustEntitledDomains()` 를 빼도 RED.** **`.exempt(...)` 를 빼도 RED.** *(빼도 초록이면 그 스위치는 테스트되지 않고 있고, D5-4~D5-8 에서 조용히 틀릴 것이다.)* **주입 적용 여부를 결과 읽기 전에 확인할 것.**
- [ ] **AC-6 (기존 단언 4개 GREEN)** — `assertNoApiOnSharedLibs` · `assertClasspathNeutrality` ×2 · `assertNoServletOnReactiveEdge`.
- [ ] **AC-7 (`./gradlew check` GREEN)** — **`BUILD SUCCESSFUL` 을 믿지 말고 XML 로 테스트 수·skipped 를 확인**할 것.

---

# Related Specs

- `docs/adr/ADR-MONO-049-framework-neutral-security-library.md` — § D2 · § D4 · § D5(단계 3) · **§ 1.3**(무테스트 사본) · **§ 1.7**(배선 실측) · **§ 1.8**(정책 축) · § 6
- `libs/java-security/.../oauth2/{AllowedIssuersValidator,TenantClaimValidator}.java`
- `libs/java-security-servlet/.../servlet/TenantClaimEnforcer.java` — **builder, 전 스위치 기본 닫힘**
- `projects/finance-platform/apps/{account,ledger}-service/.../security/ServiceLevelOAuth2Config.java` — 배선 지점
- `TASK-MONO-367` — 레거시 발급자 일몰(**이 task 밖**, 2026-08-01 게이트)

# Related Contracts

없다 — 행동 불변. 런타임 표면·계약 변화 없음.

---

# Edge Cases

- **`PublicPaths` 는 서비스마다 다른 클래스다** — `account-service` 와 `ledger-service` 가 **각자의** `PublicPaths::isPublic` 을 넘긴다. 라이브러리는 그 이름을 모른다(그게 `Predicate` 인 이유).
- **사본의 `@Component` → 공유 클래스의 `@Bean`** — 공유 클래스에 `@Component` 를 달 수 없다(라이브러리가 소비자의 컴포넌트 스캔에 얹히면 정책이 **보이지 않는 곳에서** 결정된다). `ServiceLevelOAuth2Config` 에 `@Bean` 으로 명시 배선하고, **거기서 세 스위치가 눈에 보이게** 한다.
- **`ledger-service` 에 테스트를 새로 쓰는 것은 "범위 초과" 가 아니다** — § 1.3 이 이 ADR 의 핵심 논거 중 하나이고, AC-4 가 그것을 닫는다.

# Failure Scenarios

- **스위치 하나를 빠뜨린다** → 게이트가 **좁아져** 정상 요청이 403 으로 죽거나(wildcard/entitled 누락), **넓어져** 면제가 사라진다. **기본이 닫힘이므로 누락은 항상 "좁아지는" 방향** — 즉 **터진다**. 조용하지 않다. Guard: AC-2 · AC-5.
- **테스트를 "주어만 바꾸는" 대신 단언까지 고친다** → 채택이 정책을 바꾼 증거를 지우는 것. Guard: AC-2.
- **`ledger-service` 를 테스트 없이 넘어간다** → § 1.3 이 그대로 남고, **이 ADR 의 논거 절반이 미이행**이다. Guard: AC-4.
- **`api` 로 배선한다** → § D2 위반. Guard: AC-6(`assertNoApiOnSharedLibs`).

---

# Provenance

`ADR-MONO-049` § D5 단계 3. `TASK-MONO-382`(D5-2) 가 랜딩해 게이트가 열렸다.

**D5-1·D5-2 는 아무것도 지우지 않았다. 이 단계가 처음으로 지운다** — 그래서 처음으로 **행동이 바뀔 수 있는** 단계이기도 하다. § 6 V6 이 그것을 잡는 유일한 장치이고, AC-5 의 mutation 이 V6 가 **실제로 물 수 있는지**를 증명한다.

분석=Opus 4.8 / 구현 권장=**Opus** (스위치 하나를 빠뜨리면 게이트가 좁아져 정상 요청이 403 으로 죽는다. 기본이 닫힘이라 **조용하지는 않지만**, 어느 스위치가 왜 켜져 있어야 하는지는 § 1.8 을 읽어야 안다).
