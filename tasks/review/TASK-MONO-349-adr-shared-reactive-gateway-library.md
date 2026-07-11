# Task ID

TASK-MONO-349

# Title

ADR-MONO-048 (PROPOSED) — `libs/java-gateway` 신설 결정. 게이트웨이 전수 진단이 드러낸 "보안 수정이 4개 복사본 중 3개에만 전파되고 아무도 몰랐다" 는 사태의 **기질(substrate)** 을 제거할지 결정한다

# Status

review

# Owner

monorepo

# Task Tags

- adr
- architecture
- shared-library

---

# Dependency Markers

- **선행 (완료)**: `TASK-BE-501` + `TASK-BE-502` (머지됨, PR #2409 `b83adf4c3`). **이 순서는 우연이 아니라 필수였다** — 갈라진 4개 구현을 먼저 합쳤다면 어느 도메인의 보안 동작이 바뀌는 것이 "리팩토링" 으로 위장됐을 것이다. 지금은 추출 대상 클래스들이 실제로 일치하므로 **행동 불변을 증명**할 수 있다.
- **해소 대상**: `TASK-MONO-347` (finance/erp 게이트웨이 부재 드리프트). ADR 의 D7 step 4 가 **direction A(구현)** 로 해소한다 — direction B(정책 예외)가 요구했을 "주인 없는 정책 완화" 없이.
- **후속 (ACCEPT 전에는 생성 금지)**: `TASK-MONO-350`~`353` (D7 로드맵). **ADR 이 ACCEPTED 되기 전에는 어떤 코드도 쓰지 않는다.**

---

# Goal

## 왜 ADR 인가 (HARDSTOP-09)

[`platform/shared-library-policy.md`](../../platform/shared-library-policy.md) § Change Rule:

> **New shared-library introduction or breaking expansion requires an ADR** (`docs/adr/` for monorepo-wide impact)

`libs/java-gateway` 신설은 **새 공유 라이브러리 도입 + 4개 프로젝트의 프로덕션 보안 엣지 재배선**이다. task 로 처리할 수 없다. **이 task 의 산출물은 코드가 아니라 결정 문서다.**

## 무엇을 결정하는가

`TASK-MONO-347` 조사 중 "그럼 존재하는 게이트웨이 5개는 서로 같은가?" 를 확인하려 전수 진단을 돌렸다. 결과는 예상과 달랐다.

**세 혈통이다 (복붙 5형제가 아니다)**:

| 혈통 | 멤버 |
|---|---|
| 공유 가문 | `wms` · `scm` · `fan` |
| 부분 공유 + 자체 확장 | `ecommerce` |
| **완전 독립 구현** | `iam` (`TokenValidator`·`JwksCache`·`TokenBucketRateLimiter` — 공유 클래스 **0개**) |

⇒ "게이트웨이 5개 통합" 은 애초에 틀린 프레임이다. 실제 추출 대상은 **4개**이고, 그것도 두 그룹이다. `iam` 은 드리프트한 사본이 아니라 **다른 설계**이므로 추출 대상이 아니다(ADR D2).

**측정 결과 (주석 제거·패키지 정규화, 2026-07-11 `a44a142f4` 기준)**:

- **wms/scm/fan 에서 코드 바이트 동일**: `ApiErrorEnvelope`, `GatewayErrorHandler`, `RequestIdFilter`, `RetryAfterFilter`, `SecurityConfig`, `AllowedIssuersValidator`, `FailOpenRateLimiter` — **7개**
- **`AllowedIssuersValidator` 는 ecommerce 까지 4/4 동일**
- **다른 5개**(`RateLimitConfig`, `IdentityHeaderStripFilter`, `JwtHeaderEnrichmentFilter`, `TenantClaimValidator`, `OAuth2ResourceServerConfig`)는 **각각 정확히 한 축**으로만 다르다 → 파라미터화 가능

즉 **중복은 크고 실재하며(3개 서비스에 걸쳐 6개 클래스가 바이트 동일), 변이는 작고 열거 가능하다(5개, 각 1축)**.

## 이 결정을 강제하는 사실 (ADR § 1.3 — 이게 핵심이다)

`FailOpenRateLimiter` 의 "모든 `Throwable` 을 Redis 장애로 삼킴" 버그는 **이미 한 번 인지되고 수정됐다**. 수정은 **scm·fan·ecommerce 에 도달했고 wms 에는 도달하지 않았다.**

아무도 몰랐고, **코드를 읽어서 알아낼 방법이 없었다** — 사본이 4개인데 공유 정의가 없으니 어느 것이 최신인지 판정 불가. wms 는 그동안 "필터 체인에 NPE 하나 나면 레이트리밋이 통째로 사라지는데 로그는 Redis 를 지목하는" 게이트웨이로 돌고 있었다.

**더 나쁜 것**: wms 의 그 테스트 이름이 `failsOpenOnAnyReactiveError` 였다 — **결함이 "의도된 계약" 으로 명세에 못박혀 있어** 스위트가 영원히 이의를 제기할 수 없었다.

⇒ **이 ADR 의 논거는 DRY 라는 미학이 아니다. "보안 수정이 조용히 전파에 실패했고, 코드베이스가 그것을 탐지할 수단을 주지 않았다" 는 사실이다.** 보안 경계의 사본 4개는 수정을 잃어버리는 메커니즘이다.

---

# Scope

## In Scope

1. **`docs/adr/ADR-MONO-048-shared-reactive-gateway-library.md` 를 PROPOSED 로 작성** — 진단 근거(3혈통, 클래스별 측정, § 1.3 의 실패 사례), 결정(D1~D7), 대안, 결과(부정적 결과 포함).
2. `platform/shared-library-policy.md` § Decision Rule 4문항 검증 결과를 ADR 에 명시.
3. **D5 — 테넌트 게이트 4정책이 드리프트가 아니라 의도임을 소스로 확증**하고 ADR 에 기록(아래 Architecture 참조). 이게 추출이 조용히 행동을 바꿀 수 있었던 유일한 지점이었다.
4. D7 로드맵(`TASK-MONO-350`~`353`)을 **PAUSED** 로 명시.

## Out of Scope

- **`libs/java-gateway` 코드 작성 — 일체 금지.** ACCEPT 전 코드는 곧 ADR 게이트 우회다.
- **`TASK-MONO-350`~`353` 생성** — ACCEPT 후에 spawn 한다.
- **`iam` 게이트웨이** — 독립 구현. 통합은 추출이 아니라 **재작성**이고, 행동 불변 증명이 불가능하며, 이를 요구하는 결함도 없다(ADR D2).
- **wms 의 `*` wildcard 부재를 "고치는" 것** — scm/fan 은 SUPER_ADMIN wildcard 를 받고 wms 는 안 받는다. wms javadoc 이 의도라 명시하므로 **보존**한다. 이건 실질적 운영 질문("장애 시 플랫폼 운영자가 wms 엣지에 닿을 수 있는가")이지만 **행동 변경**이고, 이 추출의 가치 전체가 "행동 변경을 몰래 끼워넣지 않는다" 에 걸려 있다. ADR 에 **미결로 기록**만 한다.
- `platform/api-gateway-policy.md` L74 조이기(헤더 4개만 명명 → 도메인들은 6~10개로 갈렸다). 드리프트의 근본 원인이지만 별건.

---

# Acceptance Criteria

- [ ] AC-1 — ADR-MONO-048 이 **PROPOSED** 상태로 존재하며, § 1.3(전파 실패 사례)이 **이 결정의 논거로 명시**되어 있다. "DRY 가 좋으니까" 로 정당화하지 않는다.
- [ ] AC-2 — 클래스별 추출 판정이 **측정에 근거**한다(주석 제거 비교). "대충 비슷하다" 가 아니라 어느 클래스가 몇 개 도메인에서 바이트 동일한지 표로.
- [ ] AC-3 — `shared-library-policy.md` § Decision Rule 4문항 **각각에 답**이 달려 있고, § Forbidden 위반이 없음이 확인된다(HARDSTOP-03 미저촉).
- [ ] AC-4 — **D5**: 테넌트 게이트 4정책(wms/scm/fan/ecommerce)이 각각 **어느 문서의 권위로 정당한지** 소스와 함께 기록. 하나라도 "드리프트인지 의도인지 모름" 이면 ADR 을 낼 수 없다(그 상태에서 파라미터화하면 기본값 선택이 곧 주인 없는 정책 결정이 된다).
- [ ] AC-5 — **D6**: 행동 불변이 **주장이 아니라 증명 의무**로 명세된다(바이트 동일 / 소비자 테스트 무수정 / mutation check). 특히 **"통과하도록 고쳐야 했던 테스트는 리팩토링 옷을 입은 행동 변경"** 이라는 규칙이 명문화된다.
- [ ] AC-6 — **부정적 결과가 정직하게 적힌다** — 공유 클래스 = 공유 폭발반경, 파라미터가 설정 언어로 번질 위험, `iam` 이 갈라진 채 남는 비용, 템플릿 sync 부담.
- [ ] AC-7 — **코드 0줄.** `libs/` 아래 신규 파일 0개(`git diff --stat` 로 확인).

---

# Related Specs

- [`platform/shared-library-policy.md`](../../platform/shared-library-policy.md) § Change Rule (**이 ADR 을 요구하는 규칙**), § Decision Rule, § Forbidden, § Review Rule
- [`platform/api-gateway-policy.md`](../../platform/api-gateway-policy.md) — 라이브러리가 **구현할** 정책(재정의하지 않는다)
- [`platform/architecture-decision-rule.md`](../../platform/architecture-decision-rule.md)
- ADR-MONO-019 § D5/D6, ADR-MONO-030 § 2.4 — D5 의 4정책 권위
- ADR-MONO-003b — Phase 5 LAUNCHED(2026-05-13) → churn 게이트는 더 이상 발사를 막지 않지만 **템플릿 sync 부담**은 남는다

# Related Contracts

없음 — 결정 문서. API/event 계약 무변경. (D7 의 후속 task 들도 **행동 불변**이 목표이므로 계약을 바꾸지 않는다.)

---

# Architecture

**D5 가 이 ADR 의 가장 위험한 지점이었다.** 테넌트 게이트가 도메인마다 다르다:

| 도메인 | 게이트 | 판정 |
|---|---|---|
| wms | 엄격 일치 + `entitled_domains` dual-accept, **wildcard 없음** | 의도(javadoc 명시) |
| scm | 일치 + `*` wildcard + dual-accept | 의도(ADR-019 D5) |
| fan | 일치 + `*` wildcard, **엔타이틀먼트 분기 없음** | **의도** — `fan` 은 `ProductCatalog.ENTRIES` 에 없어 **구독 불가 도메인**(V0019 는 `wms/scm/erp/finance` 만 시드, `omni-corp` 도 fan 미구독, `fan-platform`=`B2C_CONSUMER`). fan 은 엔타이틀먼트 평면 **밖**이라 분기는 죽은 코드가 된다 |
| ecommerce | **non-blank 면 전부 허용** | 의도(ADR-019 D5/D6 + ADR-030 §2.4) — 멀티테넌트 마켓플레이스 SaaS. 엔타이틀먼트는 **IAM 발급 시점**에 결정, 엣지는 발급을 신뢰, 테넌트 분리는 **persistence `WHERE tenant_id` + M6 cross-tenant-leak IT** 가 담보 |

**4개 전부 문서화된 결정이다.** 플래그 3개(`requireTenantMatch` / `allowWildcard` / `entitlementTrust`)로 정확히 표현되므로, 각 도메인이 현재 값을 유지하면 추출은 행동 불변이다. **하나라도 "이유를 모르겠다" 였다면 이 ADR 은 못 낸다** — 기본값 선택이 곧 주인 없는 정책 결정이 되기 때문이다.

**strip 집합은 비대칭으로 설계한다(D3)**: 도메인은 헤더를 **추가만** 할 수 있고 **뺄 수는 없다**. 빼는 것이 정확히 `TASK-BE-501`/`502` 가 고친 결함이고, 그것을 허용하는 라이브러리는 **더 예쁜 문법으로 같은 구멍을 다시 연다**.

---

# Edge Cases

- **`libs/*` 는 전부 servlet 기반인데 게이트웨이는 reactive(WebFlux)** — `libs/java-web` 에 얹으면 WebFlux/SCG 가 모든 servlet 서비스 클래스패스에 올라탄다. 그래서 **별도 모듈**(D1). 저장소에 이미 `java-web` / `java-web-servlet` 분리 선례가 있다 — 정확히 이 종류의 오염을 막으려고 만든 것이다.
- **ecommerce 의 `FailOpenRateLimiter` 는 delegate 시그니처를 일반화**(`RateLimiter<Config>`)해 놨다 — 자기 override 데코레이터를 얹기 위함. Tier 2 이관 시 시그니처 조정 필요(D7 step 3).
- **단일 소비자 클래스를 "나중에 공유할지 모르니" 승격하지 말 것**(D4) — Decision Rule 1번(2개 이상 서비스가 쓰는가)이 실패한다.
- **파라미터가 5개를 넘어가면** 그 클래스는 사실 공유 대상이 아니라는 신호다. 플래그를 더 붙이지 말고 재검토.

---

# Failure Scenarios

| # | 시나리오 | 기대/완화 |
|---|---|---|
| 1 | ADR 없이 `libs/java-gateway` 를 만든다 | § Change Rule 정면 위반 = HARDSTOP-09. **AC-7 이 코드 0줄을 강제** |
| 2 | 에이전트가 스스로 ACCEPT | **금지.** ACCEPT 는 사람의 명시 결정(`ADR-MONO-048 ACCEPTED`). 자율진행 규칙의 명시적 예외 |
| 3 | 테넌트 게이트 4정책 중 하나를 "드리프트" 로 오판해 통일 | 프로덕션 보안 동작을 조용히 변경. **AC-4 가 각 정책의 권위를 요구** |
| 4 | 추출 PR 에서 소비자 테스트를 고쳐 통과시킴 | **행동 변경이 리팩토링 옷을 입은 것.** D6/AC-5 가 금지. `failsOpenOnAnyReactiveError` 가 그 실물 |
| 5 | strip 집합을 "빼기 가능" 하게 파라미터화 | BE-501/502 가 고친 구멍을 재개방. D3 의 add-only 비대칭이 가드 |
| 6 | `iam` 까지 통합하려 함 | 추출이 아니라 **재작성**. 행동 불변 증명 불가, 동기도 없음. D2 가 배제 |
| 7 | 부정적 결과를 안 적고 ADR 을 냄 | 공유 폭발반경·템플릿 sync·iam 잔존 비용을 숨기면 결정이 아니라 영업이다. AC-6 |

---

# Test Requirements

해당 없음 — 결정 문서(코드 0줄). 검증은:

- `git diff --stat` 에 `libs/` 신규 파일 **0개**(AC-7).
- ADR 의 측정 표가 **재현 가능**해야 한다 — 주석 제거 후 md5 비교 절차를 § 1.2 에 남겨, 리뷰어가 직접 돌려볼 수 있게.
- 링크 정합(상대경로 앵커).

---

# Definition of Done

- [ ] `ADR-MONO-048` **PROPOSED** 로 작성 (D1~D7 + 대안 + 부정적 결과)
- [ ] D5 4정책 권위 확증 기록
- [ ] D6 행동-불변 증명 의무 명문화
- [ ] `libs/` 코드 0줄
- [ ] `tasks/INDEX.md` 갱신
- [ ] **사용자에게 ACCEPT 여부를 물음** — 자율 진행하지 않는다

---

# Provenance

2026-07-11. `TASK-MONO-347` → 게이트웨이 전수 진단 → `TASK-BE-501`/`502` (결함 4건 수정, PR #2409 머지) → **이 ADR**.

**진단이 두 번 뒤집혔고, 둘 다 코드로 확인해서 잡았다**:

1. **"게이트웨이 5개는 복붙"** → 틀렸다. **세 혈통**이고 `iam` 은 공유 클래스가 **0개**인 완전 독립 구현이었다. 이걸 모른 채 "5개 통합" 계획을 세웠다면 `iam` 재작성이라는 전혀 다른 리스크를 추출로 위장했을 것이다.
2. **서브에이전트가 보고한 "fan 의 `entitled_domains` 누락"** → **오탐**이었다. 참조 카운트(wms 3 / scm 5 / fan 0)만 보고 드리프트로 추정했으나, fan 이 0 인 이유는 방치가 아니라 **아키텍처**(엔타이틀먼트 평면 밖)였다. 검증 없이 "고쳤다면" **프로덕션 보안 필터에 죽은 코드를 심었을 것이다.**

⇒ **교훈: 서브에이전트의 진단 보고는 코드로 재검증해야 한다.** 그리고 리팩토링 계획은 "얼마나 갈라졌는지" 를 측정한 **뒤에** 세워야 한다 — 측정 전에 세우면 그건 계획이 아니라 추정이다.

분석=Opus 4.8 / 구현 권장=**해당 없음 (사람 결정 대기)**. ACCEPT 후 D7 step 1 착수 시 = **Opus** (리액티브 보안 필터 추출 + 파라미터화 — 단순 이동이 아니다).
