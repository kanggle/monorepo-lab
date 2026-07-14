# Task ID

TASK-MONO-411

# Title

정경 스펙 4쌍이 서로 다른 말을 한다 — 인용된 출처가 자기 자신과 어긋나는 자리 포함

# Status

review

# Owner

monorepo

# Task Tags

- adr
- api
- event

---

# Goal

`/validate-rules`(2026-07-15)가 **platform 스펙끼리 갈라진** 4쌍을 찾았다. 스펙은 SoT 다 — 갈라진 스펙은 **성실히 따를수록 틀리게 만든다**(`MONO-403`·`MONO-367` 이 같은 실패 모드로 대가를 치렀다).

**① `versioning-policy.md:9-13` 이 자기 자신과 모순** (실측 확인)
- bullet 1: *"URL path versioning: `/api/v{n}/{resource}`"*
- bullet 3: *"Version prefix is **omitted** in current contracts but must be added when a breaking change is introduced."*
- 그런데 **`rest-api.md:28` 은 이 파일을 근거로 *"the `/api/` prefix is **mandatory** per `platform/versioning-policy.md`"*** 라고 인용하고, `naming-conventions.md:64` 의 예시(`/api/v1/<resource>/refresh-token`)도 접두사를 쓴다. **인용된 출처가 인용문과 반대를 말한다.**

**② 이벤트 breaking-change 메커니즘이 두 스펙에서 다르다**
- `versioning-policy.md:35-39` — *"`{EventName}V{n}` … **Alternatively**, topic suffix (`<topic>.v2`) — either approach"* (**택1**, `eventVersion` 필드 언급 없음)
- `event-driven-policy.md:60-68,121-126` — *"Breaking changes **require**: 1. a new `eventVersion`, **and** 2. a parallel topic, 3. consumer migration deadline"* (**둘 다 필수**)
- **두 문장은 합성되지 않는다.** 구현자는 자기가 먼저 연 파일에 따라 다른 코드를 쓴다.

**③ `Idempotency-Key` 의 적용 범위**
- `platform/service-types/rest-api.md:41` — *"All non-idempotent endpoints … **MUST** accept an `Idempotency-Key` header"* (**모든 rest-api 서비스, 무조건**)
- `rules/traits/transactional.md` T1 + `error-handling.md:106-116` — **`transactional` trait 를 선언한 프로젝트에만** 활성화되는 규칙으로 서술.
- **SoT 우선순위상 `rules/traits/`(4층) > `platform/`(5층)** ⇒ **trait 게이트가 권위**이고 `rest-api.md` 의 무조건 MUST 가 고쳐질 쪽이다. 단, *"platform 베이스라인으로 격상"* 이 의도였다면 그렇게 **선언**하고 `error-handling.md` 의 분류를 함께 고쳐야 한다.

**④ JWT 서명 알고리즘**
- `platform/contracts/jwt-standard-claims.md:36` — *"Algorithm: **RSA** asymmetric"* (대안 없음)
- `platform/service-types/identity-platform.md:99-101` — *"RSA 2048 minimum, **or EdDSA (Ed25519)**"*
- 계약이 더 좁고 더 구속적이다. **둘 중 하나가 틀렸고, 지금은 어느 쪽을 믿을지가 파일 열람 순서에 달려 있다.**

---

# Scope

## In Scope

- **네 쌍을 각각 하나의 문장으로 수렴**시킨다. 매번 **어느 쪽이 권위인지 SoT 우선순위로 판정하고 그 근거를 PR 본문에 적는다.**
- ①은 **`versioning-policy.md` bullet 3 이 stale** 일 가능성이 높다(두 파일이 이미 반대로 실행 중). **다만 진짜로 접두사 없는 계약이 남아 있는지 grep 으로 확인하고 고쳐라** — 문장이 stale 인지, 아니면 현실이 갈라진 건지는 **코드가 답한다.**
- ③은 **결정이 필요**하다(trait 게이트 유지 vs platform 베이스라인 격상). **이 task 는 결정을 강요하지 않는다 — 현행 SoT 대로 trait 게이트를 정경으로 삼아 `rest-api.md` 를 정정하는 것이 기본값**이고, 격상을 원하면 **사람이 명시적으로 승인**해야 한다(ADR 필요 여부 포함).

## Out of Scope

- **코드 변경 0.** 이 task 는 스펙 간 모순을 없앤다. 스펙 수정으로 **살아있는 코드가 위반이 되는 것이 드러나면** 별건 티켓으로 제안한다(AC-4).
- 새 ADR 발행 — **먼저 정경 파일을 읽어라.** `MONO-406` 의 교훈: *"새 결정을 제안하기 전에 정경 정책부터 열 것 — 이미 결정된 것을 다시 결정하면 드리프트가 한 겹 더 쌓인다."* ③이 ADR 을 요구하는지는 `platform/architecture-decision-rule.md` 가 판정한다.

---

# Acceptance Criteria

- [x] **AC-0 (재측정)** 4쌍의 인용문이 **오늘도 그 파일 그 줄에 있는지** 직접 확인. 이미 수렴된 쌍이 있으면 phantom 으로 기록하고 넘어간다. → **4쌍 전부 실재. phantom 0건.**
- [x] **AC-1** 네 쌍 각각 **하나의 문장**으로 수렴. 각 수정마다 **권위 근거(SoT 층)** 를 명시.
- [x] **AC-2 (①은 코드에 물어라)** 접두사 없는 API 경로가 **실제로 남아 있는지** grep. **결과가 문장을 결정한다.** → 전수 측정 완료 (아래 § AC-2).
- [x] **AC-3 (③은 사람 결정)** trait 게이트 유지가 기본값. → **기본값 채택(격상 제안 안 함) ⇒ 사람 승인 불필요.** 격상 옵션은 아래 § AC-3 에 근거·영향범위와 함께 기록.
- [x] **AC-4 (스펙을 고치면 코드가 위반이 될 수 있다)** 각 수렴 후, 그 문장이 **오늘의 코드와 어긋나는지** 조사만 하고 별건 제안. → 위반 0건. 별건 후보 2건 기록 (아래 § AC-4).
- [x] **AC-5** `platform/` project-agnostic 유지(HARDSTOP-03) — 서비스명·도메인 엔티티 도입 0. → **0건. 오히려 기존 위반 1건 제거**(`rest-api.md` 의 `/api/v1/orders` 예시 = 도메인 엔티티).

---

# Related Specs

- `platform/versioning-policy.md` · `platform/event-driven-policy.md` · `platform/naming-conventions.md`
- `platform/service-types/{rest-api,identity-platform}.md`
- `platform/contracts/jwt-standard-claims.md`
- `platform/error-handling.md` § Idempotency codes · `rules/traits/transactional.md`
- `CLAUDE.md § Source of Truth Priority` (**분쟁 판정의 정경**)
- `platform/architecture-decision-rule.md` (③이 ADR 게이트인지 판정)

# Related Skills

N/A.

---

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` (④)

---

# Target Service

N/A — 공유 `platform/` 스펙.

---

# Implementation Notes

- **"둘 다 맞게 적기" 는 해결이 아니다** — 지금이 정확히 그 상태다(각 파일이 자기 말을 한다). **하나를 고르고, 다른 쪽은 그것을 가리키게 하라.** 정경=규칙 / 나머지=포인터.
- ②는 `event-driven-policy.md` 가 더 상세하고 `entrypoint.md` 의 `event` 태그가 그리로 라우팅한다 ⇒ **그쪽이 정경일 가능성이 높다.** 다만 topic-suffix 대안이 살아있는 관행이면 그걸 **event-driven-policy 에 흡수**하는 게 맞다 — 실제 토픽 이름을 세어 보고 결정하라.

---

## DONE (2026-07-15)

### AC-0 재측정 — phantom 0건

4쌍 모두 인용된 파일·줄에 **오늘도 그대로 있었다.** 서브에이전트 보고(②③④)는 전부 참. 수렴된 쌍 없음.

### 권위 판정의 공통 근거 — SoT 층이 안 갈리는 자리가 있다

①②④는 **양쪽이 똑같이 5층**(`platform/`)이라 SoT 우선순위표만으로는 안 갈린다. 갈라준 것은 세 가지:

1. **파일 자신의 자기선언.** `service-types/{rest-api,identity-platform}.md` 는 **첫 5줄에서** *"This document extends the Core platform specs. It does not replace them."* 라고 스스로 종속을 선언한다 ⇒ service-type 파일은 **구조적으로 포인터**다.
2. **`entrypoint.md § Auxiliary` 의 라우팅.** `api` 태그 → `versioning-policy.md` + `contracts/jwt-standard-claims.md`. `event` 태그 → **`event-driven-policy.md` 단독**(`versioning-policy.md` 는 event 경로에 아예 없다). **구현자가 실제로 여는 파일이 정경이어야 한다.**
3. **인용 방향.** `rest-api.md:28` 이 `versioning-policy.md` 를 *근거로 인용* 했다 ⇒ 인용한 쪽이 파생, 인용된 쪽이 정경.

③만 SoT 층이 깨끗하게 갈린다: `rules/traits/` = **4층** > `platform/` = **5층**.

### ① API 경로 접두사 + 버전 세그먼트 — 정경 = `platform/versioning-policy.md` § HTTP API Versioning

**코드가 문장을 결정했다(AC-2).** bullet 3 은 **"절반은 stale, 절반은 참"** 이었고, **거짓인 절반이 정확히 `rest-api.md` 가 반박하던 절반**이다.

- `/api/` 접두사로 읽으면 → **거짓**(외부 노출 엔드포인트 123개 중 생략 **0개**). 그래서 `rest-api.md:28` 이 같은 파일을 인용하면서 정반대를 주장하게 된 것.
- `v{n}` 세그먼트로 읽으면 → **참이지만 전부는 아님**(123개 중 92개가 생략, 31개는 명시).

⇒ 두 축을 **분리**해서 한 문장씩 못박았다: `/api/` 는 무조건 필수 / `v{n}` 은 엔드포인트의 계약(`specs/contracts/http/<service>-api.md`)이 정하고, 세그먼트 없는 계약은 **암묵 v1**, breaking change 때 명시 세그먼트 도입. `rest-api.md` § Versioning 과 `naming-conventions.md` § API Endpoints 는 **포인터로 강등**.

> **의도인가 드리프트인가**: 코드만으로는 안 갈린다(양쪽 다 살아있고 각자 계약에 적혀 있다). 그래서 수렴 문장은 **둘 다 합법화하되 "계약이 정한다"** 로 결정 지점을 못박았다 — 한쪽으로 통일하는 것은 92개 엔드포인트를 건드리는 **별개 결정**(§ AC-4 후보 1).

### ② 이벤트 breaking-change 메커니즘 — 정경 = `platform/event-driven-policy.md` § Contract Rule + § Schema Versioning

`versioning-policy.md § Event Versioning` 이 **세 겹으로 틀렸다**:

1. **"either approach"(택1)** ↔ `event-driven-policy.md` 는 두 곳(§ Contract Rule 64-67, § Schema Versioning 124)에서 일관되게 **AND**.
2. **`eventVersion` 을 아예 언급 안 함** — 그런데 그건 `libs/java-messaging/EventEnvelope.java` 의 **필수 봉투 필드**다(코드 83곳/43파일).
3. **주 메커니즘으로 제시한 `{EventName}V{n}` 이벤트타입 접미사 = 저장소 전체 사용처 0건.**

살아있는 관행 실측: 프로젝트 이벤트 계약의 **버전 토픽 91개**(`.v1` 85 / `.v2` 6). 그리고 프로젝트 계약이 **이미 event-driven-policy 를 따르고 있었다** — `projects/wms-platform/specs/contracts/events/inventory-events.md:585-586`: *"Breaking changes bump `eventVersion` **AND** publish on a new topic"*. ⇒ 흡수할 "살아있는 대안" 은 없었다. **규칙 텍스트 삭제 → 포인터.**

### ③ `Idempotency-Key` 범위 — 정경 = `rules/traits/transactional.md` T1 (SoT 4층 > 5층)

**기본값 채택. 격상 제안 안 함 ⇒ 사람 승인 불필요(AC-3).** `rest-api.md § Idempotency` 의 무조건 MUST 를 **trait 게이트로 스코프**하고 T1 을 가리키게 했다. T1 쪽에도 정경 선언을 달아 **양방향 도달성** 확보.

- **이미 두 정경 표면이 trait 게이트에 동의하고 있었다**: `error-handling.md § Transactional Trait` 이 `IDEMPOTENCY_KEY_REQUIRED`·`DUPLICATE_REQUEST` 를 **trait-activated 코드**로 등록. `rest-api.md:41` 이 **외톨이**였다.
- **격상 시 영향 범위(실측)**: `transactional` **없이** `rest-api` 인 서비스 = **정확히 1개** — `platform-console` / `console-bff`(traits: multi-tenant, integration-heavy, audit-heavy). 나머지 6개 프로젝트는 전부 `transactional` 선언. 게다가 console-bff 는 **BFF/프록시**라 자기가 상태를 만들지 않고 하위 도메인 API(전부 transactional)로 `Idempotency-Key` 를 **전달**할 뿐이다 ⇒ 스코프 축소로 뚫리는 구멍은 **실질 0**. (그래도 오독 방지용으로 "전달 의무는 하위 계약에서 온다" 를 명시했다.)
- **ADR 게이트 판정**: `platform/architecture-decision-rule.md` 는 **서비스 아키텍처 스타일**(Hexagonal/Layered/Clean) 선언 규칙이라 ③과 무관 — **ADR 게이트 아니다.** 실제 게이트는 `rest-api.md § Change Rule`: *"New constraints affecting deployed services require an ADR."* ⇒ **스코프 축소(=상위 층에 맞추는 narrowing)는 새 제약이 아니므로 ADR 불필요**. 반대로 **platform 베이스라인 격상은 배포된 서비스(console-bff)에 새 제약을 부과하므로 ADR 필요**. 격상을 원하면 그 ADR 부터.

### ④ JWT 서명 알고리즘 — 정경 = `platform/contracts/jwt-standard-claims.md` § JWT Signing Strategy

**계약이 옳았고, 코드가 계약 편이었다.**

- `EdDSA` / `Ed25519` = **저장소 Java 전체 0건**. `RS256` = 246줄.
- 결정적: 라이브 검증 경로가 **RS256 을 핀하고 나머지를 fail-closed 로 거부**한다 — `projects/iam-platform/apps/auth-service/.../oauth/OidcJwksVerifier.java:130-131` `if (!"RS256".equals(alg)) throw new OAuthProviderException("id_token alg must be RS256");`. JWKS 도 `alg: RS256` 하드코딩(`JwksEndpointProvider.java:32`, `WellKnownController.java:44`).
- ⇒ `identity-platform.md:101` 의 *"or EdDSA (Ed25519)"* 는 **아무도 구현 안 했고 런타임이 금지하는 옵션**이었다. 문서를 성실히 따른 구현자는 **자기 코드가 거부하는 토큰을 발행**했을 것이다. **계약으로 수렴**, service-type 은 포인터. 키 강도 지침(2048 min / 4096)은 **모든 relying party 가 읽는 계약 쪽으로 이동**.
- 덤: 같은 줄의 *"OPERATOR-aud signing keys"* 는 **ADR-MONO-032/TASK-MONO-263 이 제거한 `account_type` OPERATOR 개념**을 아직 참조하고 있었다 → 중립 표현으로 교체(같은 줄을 어차피 다시 쓰는 김에, 새 규칙 도입 없이).

### AC-2 실측 (전수)

컨트롤러 `@RequestMapping` **145개** 전수 분류:

| 분류 | 개수 | 분포 |
|---|---|---|
| `/api/v{n}/…` (버전 명시) | **31** | 1개 프로젝트 |
| `/api/…` (버전 세그먼트 없음) | **92** | 6개 프로젝트 |
| **외부 노출 `/api/**` 소계** | **123** | **`/api/` 접두사 생략 = 0건** |
| `/internal/…` (service-to-service) | 20 | 클라이언트 대면 아님 |
| `/webhooks/…` (인바운드 수신) | 2 | 클라이언트 대면 아님 |

**결론: 접두사는 stale 한 문장이었고(0/123 생략), 버전 세그먼트는 현실이 갈라져 있다(92 vs 31).** 하나의 bullet 이 두 질문에 답하려다 양쪽으로 읽히고 있었다.

### AC-4 — 수렴된 문장 vs 오늘의 코드: **위반 0건**

네 문장 모두 살아있는 코드와 **일치**한다(①: `/api/` 123/123 · `v{n}` 은 계약이 정한다고 썼으므로 92·31 둘 다 합법 / ②: 프로젝트 계약이 이미 AND / ③: console-bff 는 프록시 · 나머지 전부 transactional / ④: RS256 핀). **코드 변경 0.**

별건 후보 2건 (**이 task 에서 고치지 않음**):

1. **`v{n}` 세그먼트 통일 여부** — 92개는 암묵 v1, 31개는 명시 v1. 지금 규칙은 둘 다 합법화했다(코드가 그러니까). 하나로 통일하려면 **92개 엔드포인트 + 계약**을 건드리는 마이그레이션이고 배포된 서비스에 새 제약을 부과하므로 **ADR 필요**. 지금 상태가 의도라면 그것도 **명시적으로 결정**돼야 한다.
2. **🔴 `specs/contracts/events/README.md` = 아무 데도 없는 결정 지점.** `event-driven-policy.md` 가 **4곳**에서 이 파일을 정경 선언 지점으로 가리킨다(토픽 네이밍 규약, eventType 네이밍, 직렬화 선택, 스키마 레지스트리 선택). 그런데 **7개 프로젝트 중 0개**가 그 파일을 갖고 있다(계약 파일 97개 전수 확인, `specs/contracts/**` 아래 README 는 `ecommerce/schemas/README.md` 하나뿐). ⇒ **"프로젝트가 선택해서 문서화한다" 는 규칙이 도달하는 파일이 없다.** 이 task 의 ② 수렴이 event-driven-policy 를 정경으로 세웠으므로, 그 정경이 가리키는 결정 지점이 비어 있다는 건 바로 다음 드리프트 자리다. (본 task 에서 7개 README 를 저술하는 건 범위 밖.)

### 도구 주의 (실측)

`Glob` 이 이 워크트리의 절대경로에 대해 **알려진 파일이 있는 경로에 0건을 반환**했다(`projects/*/specs/contracts/**/*.md` → 0건, 실제 97개). `Get-ChildItem` 으로 교차검증해서 잡았다. **탐지식의 0건을 "없음" 으로 읽었다면 AC-4 후보 2번은 정반대 결론이 났을 것이다** — 0건은 아는 답에 먼저 돌려보고 믿을 것.

---

# Edge Cases

- **stale 문장을 "정정" 하다 옳았던 기록을 깨뜨릴 수 있다**(`MONO-403` 의 🔵 교훈: *"지금 값과 다르다" ≠ "그때 틀렸다"*). DONE task·ADR 의 역사 본문은 **고치지 않는다** — 살아있는 규칙 표면만 고친다.

---

# Failure Scenarios

- **네 쌍 중 하나를 "합쳐서" 둘 다 만족시키려 한다** → 합성 불가능한 규칙(②)이 더 모호해진다. 완화 = AC-1(하나를 고르고 근거를 적는다).
- **에이전트가 ③을 스스로 결정** → 라이브 API 계약의 범위를 사람 없이 바꾼다. 완화 = AC-3.

---

# Test Requirements

- doc-only. CI GREEN(코드 잡 skip 이 정상임을 확인 — **SKIPPED 를 통과로 읽지 말 것**).

---

# Definition of Done

- [ ] 네 쌍 수렴 + 권위 근거 기록.
- [ ] AC-2/AC-4 코드 조사 결과 기록(0건이면 0건).
- [ ] ③ 사람 승인 여부 기록.
- [ ] `tasks/INDEX.md` done entry(close chore).

---

# Provenance

2026-07-15 `/validate-rules`. ①은 **직접 실측 확인**, ②③④는 서브에이전트 보고(**PLAUSIBLE — 착수 시 재확인**).

분석=Opus 4.8 / 구현 권장=**Opus**(SoT 판정 + ADR 게이트 판단 + 계약 범위 결정. 문서 편집처럼 보이지만 판단이 본체다).
