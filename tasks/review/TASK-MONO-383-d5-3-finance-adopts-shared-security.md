# Task ID

TASK-MONO-383

# Title

`ADR-MONO-049` **D5-3** — finance 가 공유 보안 클래스를 채택하고 **사본 6개를 지운다**. **처음으로 실제로 무언가가 삭제되는 단계**

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

- [x] **AC-1 (사본이 실제로 사라졌다)** — `projects/finance-platform` 에 세 클래스 파일 **0개**. 함대 총계 **49 → 43**, `git grep` 전수 재카운트로 확인.
      **초안의 `17 / 17 / 12` 는 산술이 틀렸다**(합이 46). 실제 내역은 **16 / 16 / 11 = 43** (착수 전 18 / 18 / 13 = 49 에서 finance 가 2 / 2 / 2 를 반납). *숫자를 물려받지 말고 다시 셀 것 — 이 시리즈에서 카운트가 틀린 다섯 번째 사례다.*
- [x] **AC-2 (행동 불변 — § 6 V6)** — 통과. 단, **V6 가 실제로 물었다**: 정경 클래스가 행동을 바꾸고 있었다 → **§ 1.9 / 아래 "정경 클래스 수정" 참조**. 기존 두 테스트의 **단언은 전부 새 정책 핀으로 이전**됐고(주어만 교체), 단 하나 `publicPathBypassed` 만 형태가 바뀌었다 — 옛 테스트는 same-package 라서 `protected shouldNotFilter()` 를 직접 호출했으나 공유 필터는 다른 패키지다. **속성은 동일하되 `doFilter` 를 통해 관측**한다(오히려 강한 단언). *정책이 아니라 가시성의 문제였다.*
- [x] **AC-3 (per-domain 테스트 = 정책 핀, 허용 **과** 거부 둘 다)** — `FinanceTenantGatePolicyTest`, **서비스당 20 tests**. decode 층과 servlet 층을 **각각** 허용/거부 양쪽으로 단언하고, **두 층이 같은 판정을 내리는지**까지 단언한다.
      **결정적으로 — 이 테스트는 `ServiceLevelOAuth2Config` 에서 빈을 꺼내 쓴다.** 자기 builder 로 subject 를 새로 만들면 config 에서 스위치가 빠져도 초록이라 AC-5 가 무의미해진다.
- [x] **AC-4 (`ledger-service` 가 처음으로 커버리지를 얻는다)** — § 1.3 의 **테스트 0** 해소. 20개 단언 신설.
- [x] **AC-5 (mutation — 배선이 진짜인가)** — 세 스위치 **전부 RED 확인**:

      | 뺀 것 | gradle | 정책핀 실패 |
      |---|---|---|
      | `.allowSuperAdminWildcard()` | 1 | 2 |
      | `.trustEntitledDomains()` | 1 | 2 |
      | `.exempt(PublicPaths::isPublic)` | 1 | 1 |

      **첫 시도의 mutation 러너는 거짓 GREEN 을 냈다.** 가드가 `git diff` 를 **HEAD 기준**으로 봤는데, mutation 이 지우는 줄은 *이 task 가 방금 추가한* 줄이라 diff 에 `-` 로 나타나지 않는다 — 기준 프레임이 틀렸다(게다가 `bc` 부재로 실패 카운트는 **빈 문자열**이었다). **mutation 직전 파일을 기준으로 삼고 사라진 줄을 출력**하도록 고친 뒤에야 셋 다 RED 가 나왔다. *탐지식의 0건은 "없음"이 아니다 — 또.*
- [x] **AC-6 (기존 단언 4개 GREEN)** — `assertNoApiOnSharedLibs` OK · `assertClasspathNeutrality`(java-security, 23 artefacts) OK · `assertClasspathNeutrality`(java-security-servlet, 50) OK · `assertNoServletOnReactiveEdge`(java-gateway, 94) OK.
- [x] **AC-7 (테스트 GREEN — XML 실측)** — account 136 · ledger 422 · finance gateway 22 · java-security 95 · java-security-servlet 24 = **699 tests / 0 skipped / 0 failures / 0 errors**.

---

# 범위 이탈 — 정경 클래스를 고쳤다 (보고)

초안의 Out of Scope 는 *"정경 클래스 수정 — D5-2 에서 확정. 파라미터가 부족하다고 느껴지면 § 1.8 이 틀렸다는 뜻이니 멈추고 보고할 것"* 이었다.

**§ 1.8 은 틀리지 않았다 — 3축 모델은 맞다.** 틀린 것은 정경 클래스가 그 중 한 축(`trustEntitledDomains`)을 **401 분기에 적용하지 않은 것**이다. 새 파라미터도, 새 축도 아니고, 이미 있는 스위치를 안 보던 자리에 보게 한 **3줄**이다.

고치지 않는 선택지는 없었다. 그대로 채택했다면 **entitled 를 켠 9개 서비스에서 decoder 가 통과시킨 토큰을 enforcer 가 401 로 막는다** — 사본들이 Javadoc 에 명시적으로 *"mismatch would create a decode-pass / filter-block split"* 이라 적어두고 피하려던 바로 그 상태. AC-2(행동 불변)를 만족시킬 방법이 없다.

**라이브 취약점은 아니다** — IAM 은 `tenant_id` 가 확정된 뒤에만 `entitled_domains` 를 넣고, 부재 시 발급이 fail-closed 다. 소비자 0인 클래스의 **잠복** 결함이었고, **첫 채택이 잡았다**. 별도 hotfix task 로 쪼개지 않은 이유: main 에 라이브 결함이 없고, D5 시리즈는 직렬이라 3줄짜리 선행 task 는 머지 홉만 하나 더 만든다.

**"entitled 만 있고 tenant_id 없는 토큰을 통과시켜야 하는가"** 는 진짜 정책 질문이고 **이 리팩터링이 답할 것이 아니다.** 오늘 두 층 모두 "통과"라고 말한다. 그게 틀렸다면 **validator 에서도 틀린 것**이고 함대 전체 · 별도 ADR 의 일이다. D5 가 해서는 안 되는 일은, 행동 불변을 표방하면서 **한 층에서만 조용히 답을 바꾸는 것**이다.

상세: `ADR-MONO-049` **§ 1.9**.

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
