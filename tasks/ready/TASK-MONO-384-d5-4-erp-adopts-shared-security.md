# Task ID

TASK-MONO-384

# Title

`ADR-MONO-049` **D5-4** — erp 의 4개 서비스가 공유 보안 클래스를 채택하고 **사본 12개를 지운다** (43 → 31). 함대 최대 보유 프로젝트

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

- **근거**: [`ADR-MONO-049`](../../docs/adr/ADR-MONO-049-framework-neutral-security-library.md) — **`ACCEPTED`, 범위 A**. § D2 · § D4 · **§ D5 단계 4** · **§ 1.8**(정책 축) · **§ 1.9**(D5-3 이 잡은 decode/filter split) · § 6 (V4 · V5 · V6).
- **선행 (머지됨)**: `TASK-MONO-378`(D5-1) · `TASK-MONO-382`(D5-2) · **`TASK-MONO-383`(D5-3 — finance, squash `022c36915`)**.
- **`TASK-MONO-383` 이 만든 것을 그대로 쓴다** — 정경 `TenantClaimEnforcer` 는 D5-3 에서 **null 분기가 `trustEntitledDomains` 를 참조하도록 고쳐졌다**(§ 1.9). erp 는 entitled 를 켜는 9개 중 4개다 ⇒ **그 수정이 없었다면 erp 도 같은 split 을 맞았다.** 이 task 는 그 수정 위에 서 있다.
- **기존 단언 4개는 이 task 후에도 GREEN 이어야 한다**: `assertNoApiOnSharedLibs` · `assertClasspathNeutrality` ×2 · `assertNoServletOnReactiveEdge`.
- **후속**: D5-5 (scm — **정책 결정이 필요한 단계**, § 1.8) — **이 task 가 랜딩한 뒤** 티켓팅. **직렬 — 병렬 착수 금지.**

---

# Goal

erp 의 servlet 서비스 **4개**(`approval`, `masterdata`, `notification`, `read-model`)가 공유 클래스를 채택하고 **자기 사본 12개를 지운다**.

| 서비스 | 사본 |
|---|---|
| `approval-service` | `AllowedIssuersValidator` · `TenantClaimValidator` · `TenantClaimEnforcer` |
| `masterdata-service` | 동일 3개 |
| `notification-service` | 동일 3개 |
| `read-model-service` | 동일 3개 |

**43 → 31.** **함대에서 가장 많은 사본을 보유한 프로젝트**이고, 한 프로젝트에서 지우는 최대 수량이다.

## erp 의 정책 (착수 전 재실측할 것 — 아래는 D5-3 시점의 관측이다)

**4개 서비스 전부 동일**하고, **finance 와도 동일**하다:

| 축 | erp |
|---|---|
| wildcard `"*"` | **허용** (4/4) |
| `entitled_domains` | **신뢰** (4/4) |
| 면제 경로 | **`PublicPaths.isPublic`** (4/4 — 각 서비스 자기 클래스) |

⇒ 배선은 D5-3 과 **같은 모양**이다:

```java
TenantClaimEnforcer.forTenant(expectedTenantId)
        .exempt(PublicPaths::isPublic)
        .allowSuperAdminWildcard()
        .trustEntitledDomains()
        .build();
```

**그렇다고 복붙하지 말 것.** § 1.8 이 존재하는 이유가 *"같아 보이는 13개가 실제로는 8개 body 였다"* 이다. **각 서비스의 `shouldNotFilter` 와 validator 생성자를 직접 읽고 확인**한 뒤 배선하라. 위 표가 틀렸다면 그 사실 자체가 산출물이다.

## 패키지 배치가 서비스마다 다르다 (D5-3 에는 없던 변수)

finance 는 두 서비스가 대칭이었다. **erp 는 아니다** — 실측:

| 서비스 | validator 위치 | config 위치 |
|---|---|---|
| `approval` · `masterdata` | `infrastructure/security/` | `infrastructure/security/ServiceLevelOAuth2Config` |
| `notification` · `read-model` | `config/security/` | **`config/ServiceLevelOAuth2Config`** |

⇒ **경로를 하드코딩한 스크립트로 훑지 말 것.** 서비스별로 읽어라.

---

# Scope

## In Scope

1. **`build.gradle` 배선** — 4개 서비스에 `implementation project(':libs:java-security-servlet')`. **`libs:java-security` 는 4개 전부 이미 선언하고 있다**(실측). **`api` 금지**(§ D2).
2. **사본 12개 삭제** + 참조하는 배선 클래스의 import 갱신.
   **⚠️ import 로 grep 하지 말 것 — 심볼로 grep 하라.** D5-1(숨은 사이클)과 D5-3(finance 의 `SecurityErrorHandler` · `ActorContextJwtAuthenticationConverter`)에서 **두 번 걸린 함정**이다: 참조자가 validator 와 **같은 패키지면 `import` 줄이 아예 없다.**
3. **정경 `TenantClaimEnforcer` 를 `@Bean` 으로 명시 배선** — `@Component` 금지(라이브러리가 소비자의 컴포넌트 스캔에 얹히면 정책이 보이지 않는 곳에서 결정된다).
4. **per-service 정책 핀 테스트** — 아래 AC-3.

## Out of Scope

- **scm · fan · wms · iam** — D5-5 ~ D5-8.
- **정경 클래스 수정** — D5-2 확정 + D5-3 § 1.9 수정. **여기서 또 고쳐야 한다고 느껴지면 멈추고 보고**하라(§ 1.8 의 3축이 부족하다는 증거다).
- **레거시 발급자 `iam` 제거** — `TASK-MONO-367`, **2026-08-01 게이트**. 건드리지 말 것.
- **erp gateway-service** — 이미 lib 판(`TenantClaimValidator.forTenant`)을 쓴다. 사본 없음.

---

# Acceptance Criteria

- [ ] **AC-1 (사본이 사라졌다)** — `projects/erp-platform` 에 세 클래스 파일 **0개**. 함대 총계 **43 → 31**. **`git grep` 전수 재카운트로 확인할 것 — 이 혈통에서 카운트가 다섯 번 틀렸다**(4 → 10 → 18 → 49, 그리고 D5-3 초안의 `17/17/12` 는 합이 46 이었다). **선행 문서의 숫자는 출처가 아니라 가설이다.**
- [ ] **AC-2 (행동 불변 — § 6 V6)** — erp 4개 서비스 스위트가 통과한다. **기존 테스트가 단언하던 *동작*은 바뀌면 안 된다** — 주어(subject)만 사본에서 공유 클래스로 바뀐다. *단언을 고쳐야 했다면 채택이 정책을 바꾼 것이다.* **D5-3 에서 V6 는 실제로 물었고, 문 것은 채택하는 쪽이 아니라 정경 클래스였다**(§ 1.9). 같은 태도로 임할 것.
- [ ] **AC-3 (정책 핀 = 허용 **과** 거부, 그리고 두 층의 합의)** — **서비스 4개 각각**:
  - `tenant_id=erp` → 통과 / `"*"` → 통과 / `entitled_domains=[erp]` + `tenant_id=finance` → 통과
  - `tenant_id=finance`, entitled 없음 → **403 TENANT_FORBIDDEN** / `tenant_id` 부재 → **401**
  - `PublicPaths` 면제 경로 → 게이트 건너뜀 / **비면제 경로(`/actuator/env` 등) → 게이트 적용**
  - **decoder 와 enforcer 가 같은 판정을 내리는가** (§ 1.9 — D5-3 의 `theTwoLayersAgree`)
  **거부 쪽이 절반이다.** 허용만 기록하는 스위트는 스위치가 사라져도 초록이다(MONO-355).
- [ ] **AC-4 (테스트 없는 사본 해소)** — **`approval-service` 와 `notification-service` 는 `TenantClaimEnforcer` 테스트가 0개다**(실측 — masterdata·read-model 만 보유). AC-3 이 4개 전부에 존재해야 한다.
- [ ] **AC-5 (mutation — 배선이 진짜인가)** — 각 서비스 config 의 builder 에서 **`.allowSuperAdminWildcard()` / `.trustEntitledDomains()` / `.exempt(...)` 를 하나씩 빼면 그 서비스 스위트가 RED**.
      **⚠️ 정책 핀은 `ServiceLevelOAuth2Config` 에서 빈을 꺼내 써야 한다.** 테스트가 자기 builder 로 subject 를 새로 만들면 **config 에서 스위치가 빠져도 초록**이고 이 AC 는 연극이 된다(D5-3 에서 이걸 못박았다).
      **⚠️ mutation 러너의 기준 프레임을 확인하라.** D5-3 의 첫 러너는 `git diff` 를 **HEAD 기준**으로 봐서 **거짓 GREEN** 을 냈다 — mutation 이 지우는 줄은 *그 task 가 방금 추가한* 줄이라 diff 에 `-` 로 나타나지 않는다. **mutation 직전 파일을 기준 삼고, 사라진 줄을 출력해서 눈으로 확인한 뒤** 결과를 읽어라.
- [ ] **AC-6 (기존 단언 4개 GREEN)** — `assertNoApiOnSharedLibs` · `assertClasspathNeutrality` ×2 · `assertNoServletOnReactiveEdge`. **artefact 수가 변하면 무언가 잘못 들어온 것이다**(D5-3 기준: 23 / 50 / 94).
- [ ] **AC-7 (테스트 GREEN — XML 실측)** — **`BUILD SUCCESSFUL` 을 믿지 말 것.** 테스트 수 · skipped 를 XML 로 확인하라(Docker 부재 시 전건 SKIPPED 인데도 성공이 뜬다).

---

# Related Specs

- `docs/adr/ADR-MONO-049-framework-neutral-security-library.md` — § D2 · § D4 · § D5(단계 4) · **§ 1.7**(배선 실측) · **§ 1.8**(정책 축) · **§ 1.9**(decode/filter split) · § 6
- `libs/java-security/.../oauth2/{AllowedIssuersValidator,TenantClaimValidator}.java`
- `libs/java-security-servlet/.../servlet/TenantClaimEnforcer.java` — builder, 전 스위치 기본 닫힘
- **`TASK-MONO-383`** (`tasks/done/`) — **D5-3 이 참조 구현이다.** finance 의 `ServiceLevelOAuth2Config` + `FinanceTenantGatePolicyTest` 를 읽고 같은 모양으로 갈 것.
- `TASK-MONO-367` — 레거시 발급자 일몰(**이 task 밖**, 2026-08-01 게이트)

# Related Contracts

없다 — 행동 불변. 런타임 표면·계약 변화 없음.

---

# Edge Cases

- **`PublicPaths` 는 서비스마다 다른 클래스다** — 4개 서비스가 **각자의** `PublicPaths::isPublic` 을 넘긴다. 라이브러리는 그 이름을 모른다(그게 `Predicate` 인 이유).
- **패키지 배치가 서비스마다 다르다** (위 표) — 경로 하드코딩 금지.
- **`org_scope` 는 이 task 와 무관하다** — `masterdata-service` 가 읽는 데이터-스코프 claim 이고, tenant 게이트가 아니다. 건드리지 말 것.
- **erp gateway 는 사본이 없다** — 이미 lib 판을 쓴다.

# Failure Scenarios

- **스위치 하나를 빠뜨린다** → 게이트가 **좁아져** 정상 요청이 403/401 로 죽는다. **기본이 닫힘이므로 누락은 항상 터지는 방향** — 조용하지 않다. Guard: AC-2 · AC-5.
- **정책 핀이 자기 builder 로 subject 를 만든다** → AC-5 가 연극이 되고, **D5-5~D5-8 에서 조용히 틀린다.** Guard: AC-5 의 명시 요구.
- **import 로 grep 해서 same-package 참조를 놓친다** → 컴파일 실패(시끄러움) 또는 **더 나쁘게, 참조자를 못 찾아 사본을 못 지운다**. Guard: 심볼 grep.
- **4개가 같아 보인다고 하나만 읽고 복붙한다** → § 1.8 이 정확히 그 가정을 깨뜨렸다(13개가 8개 body). Guard: AC-3 이 서비스별로 요구.
- **`api` 로 배선한다** → § D2 위반. Guard: AC-6.

---

# Provenance

`ADR-MONO-049` § D5 단계 4. `TASK-MONO-383`(D5-3) 이 랜딩해 게이트가 열렸다.

**D5-3 이 실제 리스크를 이미 태웠다** — 정경 Enforcer 의 decode/filter split(§ 1.9)이 **entitled 를 켜는 9개 서비스 전부**에 걸리는 결함이었고, erp 4개가 그 중 4다. **그게 D5-3 에서 잡혔으므로 D5-4 는 그 위를 걷는다.** 남은 위험은 **12개를 한 번에 지우는 물량**과 **패키지 배치의 비대칭**이지, 새로운 종류의 위험이 아니다.

분석=Opus 4.8 / 구현 권장=**Opus** (12개 삭제 · 4개 서비스 · 스위치 3개 × 4 = 배선 실수의 표면이 넓다. 다만 D5-3 이 참조 구현을 남겼으므로 판단의 난이도보다 **성실함**이 요구되는 단계다).
