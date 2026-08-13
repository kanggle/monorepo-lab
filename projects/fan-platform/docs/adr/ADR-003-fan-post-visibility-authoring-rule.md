# ADR-003: `FAN_POST` 의 가시성 티어 — 팬이 게이팅된 글을 쓸 수 있는가

**Status:** Proposed
**Date**: 2026-08-06 (proposed)
**Deciders**: kanggle
**Supersedes**: —
**Relates to**: `TASK-FAN-BE-047`(이 ADR 이 게이트하는 티켓), `TASK-FAN-FE-016`(발굴 · 작성 화면은 이미 PUBLIC 고정), `specs/contracts/http/community-api.md` § `POST /api/community/posts`, `specs/services/community-service/architecture.md` § Visibility Tiers, `specs/integration/v1-e2e-scenarios.md` § Scenario 3, `tests/e2e/.../VisibilityTierE2ETest.java`

---

## Context

`PublishPostUseCase` 는 `postType` 만 검사한다. `visibility` 는 **어디서도 검사되지 않고**
`Post.createDraft` 로 그대로 넘어간다:

```java
// PublishPostUseCase#execute — 검사는 이것 하나뿐이다 (2026-08-06 재확인)
if (cmd.postType() == PostType.ARTIST_POST
        && !actor.hasRole(ROLE_ARTIST) && !actor.isOperator()) {
    throw new PermissionDeniedException(...);
}
...
Post draft = Post.createDraft(postId, actor.tenantId(), actor.accountId(),
        cmd.postType(), cmd.visibility(), ...);   // ← visibility 무검사
```

따라서 `FAN` 역할만 가진 계정이
`POST /api/v1/community/posts {"postType":"FAN_POST","visibility":"PREMIUM"}` 를 보내면 **201** 이다.

`TASK-FAN-FE-016` 은 이것을 발굴하고 **화면 쪽에서만** 막았다(작성 폼은 `PUBLIC` 고정, 바디 전체를
단언하는 테스트로 고정). API 쪽 결정을 이 ADR 이 다룬다.

### 🔴 이 ADR 이 필요한 이유 — 발굴 티켓의 전제가 틀렸다

`TASK-FAN-BE-047` 은 *"티어는 아티스트 수익화를 위해 존재한다(`architecture.md` § Visibility
Tiers)"* 를 근거로 들었다. **그 절은 그런 말을 하지 않는다.** 실제 § Visibility Tiers 는
**읽기 게이팅 메커니즘만** 기술한다 — 어떤 티어가 누구에게 보이는지, `MembershipChecker` 가
fail-closed 인지. **누가 어떤 티어로 쓸 수 있는가는 어느 스펙에도 없다.** 그 문장은 발굴 당시의
추론이 인용처럼 적힌 것이다(같은 세션에서 작성된 티켓이라 출처가 검증되지 않았다).

그리고 침묵보다 강한 사실이 하나 더 있다 — **스펙이 반대를 적극적으로 규정한다**:

`specs/integration/v1-e2e-scenarios.md` § Scenario 3 (`VisibilityTierE2ETest`) 는
**팬이 `FAN_POST` 를 `PREMIUM` 과 `MEMBERS_ONLY` 로 발행하는 것을 시나리오로 명시**하고,
실제 e2e 테스트가 그대로 구현되어 있다(문서만이 아니라 산출물로 확인):

```java
// VisibilityTierE2ETest — 실제 코드
{ "postType": "FAN_POST", "visibility": "PREMIUM",     "title": "PREMIUM visibility test" }
{ "postType": "FAN_POST", "visibility": "MEMBERS_ONLY", "title": "MEMBERS_ONLY visibility test" }
```

⇒ `FAN_POST` 를 PUBLIC 으로 좁히는 것은 **드리프트 교정이 아니다.** 기존 스펙이 요구하는
동작을 없애는 것이고, **명시된 e2e 시나리오를 깨뜨린다.**
`TASK-FAN-BE-047` 자신이 제시한 판정 기준 — *"기존 티어 정의를 **적용**하는 것이면 드리프트
교정, 새 제약을 **도입**하는 것이면 ADR"* — 에 그대로 걸린다. **새 제약 도입이므로 ADR.**

### 실측 (2026-08-06, 모두 재측정)

| 항목 | 결과 |
|---|---|
| `PublishPostUseCase` 가 `visibility` 를 보는가 | **안 본다**(위 코드) |
| 기존 비-PUBLIC `FAN_POST` 행 | **0건**(데모 DB: `FAN_POST` 2건 전부 PUBLIC / `ARTIST_POST` 는 PUBLIC 2 · MEMBERS_ONLY 1 · PREMIUM 1) |
| `UpdatePostUseCase` 로 나중에 바꿀 수 있는가 | **불가능** — 시그니처가 `(postId, actor, title, body, mediaRefs)` 뿐이고 `visibility` 를 받지 않는다. 발행 후 가시성은 **불변** |
| 운영자/아티스트가 팬 글을 대신 만드는 경로 | **없다** — `PublishPostUseCase` 는 저자를 `actor.accountId()` 로 고정한다(그 자체가 `TASK-FAN-BE-045` 의 주제) |

🔵 세 번째 줄은 티켓의 Failure Scenario(*"발행만 막고 `UpdatePostUseCase` 를 빼먹는다 — 팬이
PUBLIC 으로 만든 뒤 PREMIUM 으로 고친다"*)를 **도달 불가로 만든다**. 그 시나리오는 존재하지 않는
경로를 걱정한 것이다.

### 무엇이 실제로 이상한가

유출은 없다. 게이팅은 정상 작동하며, 팬의 `PREMIUM` 글은 프리미엄 구독자에게만 보인다.
이상한 것은 **경제적 의미**다:

- 멤버십은 **플랫폼 스코프**다(`MembershipChecker.hasAccess(accountId, tier, tenantId)` — 아티스트별이 아니다).
- 그래서 팬의 `PREMIUM` 글은 "플랫폼 프리미엄 구독자" 에게 보이고, 그 구독료는 플랫폼이 받는다.
- **글쓴이에게는 수익이 없고, 독자층은 좁아지기만 한다.**

즉 결함이라기보다 **제품이 정의하지 않은 상태**다. 그리고 정의되지 않았다는 것이 바로
이것이 결정 사항인 이유다.

---

## Decision Drivers

- **D1 — 스펙 일관성.** 계약(`community-api.md`)·e2e 시나리오·구현이 현재는 서로 일치한다
  (셋 다 세 값을 허용). 어느 하나만 바꾸면 세 곳이 갈라진다.
- **D2 — 되돌릴 수 있는가.** 좁히는 방향은 **비가역에 가깝다**: 좁힌 뒤 다시 넓히면 그 사이에
  거절된 사용자 경험은 복구되지 않고, 좁히는 순간 기존 e2e 를 고쳐야 한다.
- **D3 — 실제 위험.** 유출 없음, 기존 데이터 0건. **지금 아무도 다치지 않는다** — 즉 서두를
  이유가 없고, 결정 품질을 우선할 여유가 있다.
- **D4 — 데모 가치.** 이 저장소의 현재 목적 중 하나는 면접용 단일 계정 데모다. 팬이 게이팅된
  글을 쓸 수 있다는 사실은 데모에서 **한 번도 드러나지 않는다**(작성 화면이 PUBLIC 고정).

---

## Options

### A. `FAN_POST` 는 `PUBLIC` 만 허용 (API 에서 400/403 거절)

- **+** "티어 = 아티스트 수익화" 라는 제품 의미가 코드에 적힌다. 화면과 API 가 일치한다.
- **−** **`VisibilityTierE2ETest` 두 케이스를 고쳐야 한다**(`FAN_POST` → `ARTIST_POST` 로 바꾸거나
  시나리오를 재작성). 명시된 스펙 문서(`v1-e2e-scenarios.md` § Scenario 3)도 함께 수정.
- **−** 계약을 좁히는 breaking change. 외부 소비자는 없지만 계약 문서가 v1 로 공표돼 있다.
- **−** D2 — 되돌리기 어렵다.

### B. 명시적으로 허용하고 **계약에 근거를 적는다** (코드 무변경)

- **+** 스펙 3곳이 계속 일치한다. e2e 무변경. 되돌릴 여지를 남긴다(D2).
- **+** 지금 상태가 "잊혀진 구멍" 이 아니라 **의도된 상태**가 된다 — 문서화가 곧 결정이다.
- **−** "말이 안 되는 상태" 가 남는다. 팬이 아무도 못 읽고 수익도 없는 글을 만들 수 있다.
- **−** 나중에 아티스트별 멤버십이 생기면 이 결정을 다시 열어야 한다.

### C. 티어의 의미 자체를 재정의 — 멤버십을 **아티스트별**로 스코프

- **+** 팬의 게이팅 글도 의미를 갖게 된다(그 팬의 구독자에게 보인다).
- **−** `MembershipChecker`·membership-service·구독 모델 전체를 건드린다. **이 티켓의 범위를
  아득히 넘는다.**
- 여기서는 **기각**하되, A 를 고를 때 "미래에 C 로 가면 A 를 되돌려야 한다" 는 점을 기록해 둔다.

---

## 권고 (결정 아님 — ACCEPT 대기)

**B 를 권고한다.** 근거는 취향이 아니라 D2 + D3 이다:

지금 이 상태로 **다치는 사람이 없고**(유출 없음, 기존 행 0건, 작성 화면은 이미 PUBLIC 고정),
반면 A 는 **명시된 스펙과 그것을 구현한 e2e 를 깨면서** 되돌리기 어려운 방향으로 간다.
"이상하다" 는 느낌만으로 비가역 방향을 선택할 이유가 없다.

B 를 고르면 `TASK-FAN-BE-047` 의 AC-1 은 *"허용하기로 했다면 그 결정과 근거가 계약 문서에
적혀 있다"* 분기로 충족되고, 구현은 **계약 문서 갱신뿐**이다.

🔴 **A 를 고른다면** 반드시 함께 해야 하는 것: `v1-e2e-scenarios.md` § Scenario 3 재작성 +
`VisibilityTierE2ETest` 의 두 케이스를 `ARTIST_POST` 로 전환. 그러지 않으면 머지 즉시
e2e 가 빨개진다. AC-2(`ARTIST_POST` 의 비-PUBLIC 은 여전히 가능)는 그 전환으로 자연히 지켜진다.

---

## Consequences

**B 채택 시**
- `community-api.md` 에 "두 postType 모두 세 값 허용" 의 **근거**가 적힌다(현재는 표만 있고 이유가 없다).
- 코드·e2e 무변경. `TASK-FAN-BE-047` 은 계약 문서 PR 하나로 닫힌다.
- 아티스트별 멤버십(C)이 생기면 이 ADR 을 supersede 한다.

**A 채택 시**
- `PublishPostUseCase` 에 `visibility` 검사 추가(+ 400/403 계약). `UpdatePostUseCase` 는
  `visibility` 를 받지 않으므로 **추가 조치 불필요**(실측).
- `v1-e2e-scenarios.md` + `VisibilityTierE2ETest` 동반 수정 — **누락 시 e2e RED**.
- 기존 데이터 마이그레이션 불필요(비-PUBLIC `FAN_POST` 0건).

---

## Status Gate

이 ADR 은 **PROPOSED** 다. `platform/architecture-decision-rule.md` § The ACCEPTED Gate 에 따라
`ADR-FAN-003 ACCEPTED` 형태의 명시적 인간 결정만이 PAUSE 를 해제한다 — 일반적인 "진행" 은
ADR 을 수락하지 않으며, 제안한 에이전트가 스스로 ACCEPT 할 수 없다.

**차단된 것**: `TASK-FAN-BE-047` 의 구현 전부(AC-1 이 A/B 중 어느 분기인지에 의존).
