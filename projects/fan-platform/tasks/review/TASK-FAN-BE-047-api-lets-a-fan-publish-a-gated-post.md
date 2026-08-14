# Task ID

TASK-FAN-BE-047

# Title

API 는 팬이 유료 글을 쓰는 것을 허용한다 — `PublishPostUseCase` 가 `postType` 만 검사하고 `visibility` 는 아무도 검사하지 않는다

# Status

review

# Owner

fan-platform

# Task Tags

- backend
- community

---

# 배경

`TASK-FAN-FE-016`(팬 작성 화면) AC-2 가 발굴했다. 그 티켓은 **화면 쪽**에서 결정을 내렸고
(작성 화면은 `visibility: PUBLIC` 만 보낸다, 테스트로 고정), API 쪽은 손대지 않은 채 남겼다.
이 티켓이 그 나머지다.

## 실측 (2026-08-06)

```java
// PublishPostUseCase#execute — 검사는 이것 하나뿐이다
if (cmd.postType() == PostType.ARTIST_POST
        && !actor.hasRole(ROLE_ARTIST) && !actor.isOperator()) {
    throw new PermissionDeniedException(...);
}
// cmd.visibility() 는 그대로 Post.createDraft 로 넘어간다 — 어디서도 검사되지 않는다
```

즉 `FAN` 역할만 가진 계정이
`POST /api/v1/community/posts {"postType":"FAN_POST","visibility":"PREMIUM",...}` 를 보내면
**201 이 나온다.**

## 왜 문제인가 — "권한" 이 아니라 **말이 되지 않는다**

유출은 없다. 오히려 반대로 **아무도 읽을 수 없는 글**이 생긴다:

- 게이팅은 `MembershipChecker.hasAccess(accountId, tier, tenantId)` 로 판정되고,
  멤버십은 **플랫폼 스코프**다(아티스트별이 아니다).
- 그래서 팬의 `PREMIUM` 글은 **프리미엄 구독자에게만** 보이는데, 그 구독료는 플랫폼이
  받는다. 글쓴이에게는 수익도, 독자도 없다.

---

## 🔴 정정 (2026-08-06, 착수 시 AC-0 재측정) — 이 티켓의 전제 한 줄이 틀렸다

원문에는 *"티어는 **아티스트 수익화**를 위해 존재한다(`architecture.md` § Visibility Tiers)"* 가
있었다. **그 절은 그런 말을 하지 않는다.** 실제 § Visibility Tiers 는 **읽기 게이팅 메커니즘만**
기술한다(어떤 티어가 누구에게 보이는지, `MembershipChecker` 가 fail-closed 인지). **누가 어떤
티어로 쓸 수 있는가는 어느 스펙에도 없다.** 그 문장은 발굴 당시(`TASK-FAN-FE-016` 과 같은 세션)의
**추론이 인용처럼 적힌 것**이라 삭제했다.

그리고 침묵보다 강한 사실이 있다 — **스펙이 반대를 적극적으로 규정한다**:
`specs/integration/v1-e2e-scenarios.md` § Scenario 3 은 팬이 `FAN_POST` 를 `PREMIUM` 과
`MEMBERS_ONLY` 로 발행하는 것을 **시나리오로 명시**하고, `VisibilityTierE2ETest` 가 그대로
구현하고 있다(문서가 아니라 산출물로 확인).

⇒ 좁히는 것은 **드리프트 교정이 아니라 새 제약 도입**이며, 아래 체크리스트 네 번째 항목이
요구한 판정에 따라 **ADR 이 필요하다**.
판정·실측·선택지 전부: [`docs/adr/ADR-003-fan-post-visibility-authoring-rule.md`](../../docs/adr/ADR-003-fan-post-visibility-authoring-rule.md) (**PROPOSED**).

~~**이 티켓은 그 ADR 이 ACCEPTED 되기 전까지 착수할 수 없다.**~~ → **해제 2026-08-14** — `ADR-FAN-003 ACCEPTED — B`(소유자 정확형). 🔴 게이트가 실제로 물었다 — 먼저 도착한 것은 **`B` 한 글자**였고, 글자는 소유자가 직접 타이핑했지만 `architecture-decision-rule.md` § The ACCEPTED Gate 가 *"names the ADR"* 를 요구하므로 넘기지 않고 정확형을 다시 받았다.

# Goal

팬이 만들 수 있는 게시물의 가시성이 **코드에 적혀 있다** — 화면에만 적혀 있지 않다.

---

# Scope

## In Scope

- `PublishPostUseCase`(및 필요 시 `UpdatePostUseCase`)의 `visibility` 규칙
- `specs/contracts/http/community-api.md` — 지금은 두 postType 모두에 대해 세 값을 전부
  허용한다고 적혀 있다. **계약이 먼저다**
- 테스트

## Out of Scope

- 작성 화면 — `TASK-FAN-FE-016` 에서 이미 PUBLIC 고정 + 테스트

---

# 🔴 착수 전에 반드시 확인할 것 — **전부 2026-08-06 에 실측 완료**

- [x] **기존 데이터** — 비-PUBLIC `FAN_POST` 행: **0건**(데모 DB: `FAN_POST` 2건 전부 PUBLIC,
      `ARTIST_POST` 는 PUBLIC 2 · MEMBERS_ONLY 1 · PREMIUM 1). 다른 환경은 다시 셀 것
- [x] **`UpdatePostUseCase` 로 나중에 바꿀 수 있는가** → **불가능**. 시그니처가
      `(postId, actor, title, body, mediaRefs)` 뿐이고 `visibility` 를 받지 않는다 —
      발행 후 가시성은 **불변**이다. ⇒ 아래 Failure Scenario 의 "PUBLIC 으로 만든 뒤 PREMIUM
      으로 고친다" 는 **도달 불가한 경로**였다(존재하지 않는 위험을 걱정한 것)
- [x] **운영자/아티스트가 팬 글을 대신 만드는 경로** → **없다**. `PublishPostUseCase` 는 저자를
      `actor.accountId()` 로 고정한다(그 고정 자체가 `TASK-FAN-BE-045` 의 주제)
- [x] **ADR 이 필요한지** → **필요하다**. 스펙이 침묵하는 게 아니라 **반대를 규정**하기 때문
      (§ 정정 참조). ⇒ `ADR-003` **PROPOSED**

# Acceptance Criteria

> **결정 = [`ADR-003`](../../docs/adr/ADR-003-fan-post-visibility-authoring-rule.md) ACCEPTED — B.**
> 세 티어를 계속 허용하고 **코드는 바꾸지 않는다.** 그래서 AC-1 은 "거절" 이 아니라 "근거를
> 계약에 적는다" 분기로 닫힌다. 🔵 산출물이 문서와 가드뿐인 것은 미달이 아니라 **B 의 정의**다.

- [x] **AC-0 (재측정) — 완료 2026-08-14.** ① `PublishPostUseCase` 는 여전히 `visibility` 를
      **안 본다**(L43 `cmd.visibility()` 가 유일한 등장, 검사 0) ② `UpdatePostUseCase.execute(
      postId, actor, title, body, mediaRefs)` — `visibility` **없음**, 발행 후 불변 ③ 대신
      만들어 주는 경로 없음(저자 = `actor.accountId()` 고정) ④ ADR 필요 → **ACCEPTED**.
      🔵 곁다리: 티켓 § 정정이 이미 적용돼 있음을 확인했고(*"티어는 아티스트 수익화를 위해
      존재한다"* 인용은 2026-08-06 삭제), `architecture.md` § Visibility Tiers 를 직접 열어
      **읽기 게이팅 표뿐 저작 규칙 0글자**임을 재확인했다
- [x] **AC-1 — 완료.** `community-api.md` § `POST /api/community/posts` 에
      *"`visibility` is unrestricted by `postType` — deliberately"* 절 신설. 표가 담을 수 없는
      **근거 4가지**(유출 없음·경제적 이상함 / 스펙이 반대를 **적극 규정**(Scenario 3 + e2e) /
      `visibility` write-once / 제품 표면이 선택지를 안 준다)와 재개봉 조건을 적었다
- [x] **AC-2 (음성 대조) — 완료.** `ARTIST_POST` 의 비-PUBLIC 은 여전히 된다 — **코드 무변경**
      이라 자동으로 지켜진다. `git diff -- apps/ tests/ specs/integration/` **비어 있음**으로
      확인(게이팅·`VisibilityTierE2ETest`·Scenario 3 전부 무변경). community-service 유닛 GREEN
- [x] **AC-3 — 완료.** 계약이 적는 것과 코드가 하는 것이 일치한다. 🔴 **B 에서 이 AC 가 가장
      약하다 — 코드를 안 바꿨으므로 원래부터 일치했다.** 그래서 "일치를 만들었다" 가 아니라
      **"일치가 유지되는지 지키는 것"** 을 AC-4/AC-5 로 뒀다
- [x] **AC-4 (재개봉 트리거에 숫자를 준다) — 완료. `ADR-003` § 결정 이 승격한 항목.**
      ADR 은 *"아티스트별 멤버십이 생기면 supersede"* 라고만 적었는데 **아무도 그 문장을
      평가하지 않으므로 영영 참이 되지 않는다.** ⇒ `MembershipScopeIsPlatformWideTest` 신설:
      `MembershipChecker` 포트가 **저자/아티스트 축을 갖지 않음**을 단언하고, 실패 메시지가
      *"버그가 아니라 트리거다 — 테스트를 고치지 말고 ADR-003 을 다시 열어라"* 를 말한다.
      🔴 **행위가 아니라 포트의 형태를 단언한 것이 핵심** — *"팬이 PREMIUM 을 쓸 수 있다"* 류
      행위 테스트는 **바로 그 변경을 통과시킨다**(아티스트별 멤버십은 발행을 막지 않고 발행의
      *의미*를 바꾼다). 움직이는 관측량은 포트의 저자 의존성이다.
      **bite**: ① 포트에 메서드 추가(컴파일 유지) → RED ② 단언을 4-파라미터로 겨눔 → RED,
      둘 다 복원 후 GREEN
- [x] **AC-5 (B 의 가역성이 얹힌 가드를 명시) — 완료. 같은 승격 항목.**
      B 가 되돌릴 수 있는 근거는 *"게이팅된 `FAN_POST` 행이 쌓이지 않는다"* 이고, 그 이유가
      작성 화면의 `visibility:'PUBLIC'` 하드코딩 + `fan-post-publish-action.test.ts` 의 **바디
      전체 `toEqual`** 이다. **사실이지 보장이 아니었다** — `toMatchObject` 로 완화하면 행이
      쌓이기 시작하고 **가드가 아니라 ADR-003 의 전제가 무너진다**. 그 인과를 주석에 박아
      완화가 **결정 변경으로 읽히게** 했다(vitest 7건 GREEN)

# Related Specs

- `apps/community-service/.../application/PublishPostUseCase.java`
- `specs/contracts/http/community-api.md` § `POST /api/community/posts`
- `specs/services/community-service/architecture.md` § Visibility Tiers

# Edge Cases

- 이미 존재하는 비-PUBLIC `FAN_POST` — 거절 규칙을 넣으면 그 행은 읽기는 되지만 수정이
  막힐 수 있다

# Failure Scenarios

- ~~**발행만 막고 `UpdatePostUseCase` 를 빼먹는다** — 팬이 PUBLIC 으로 만든 뒤 PREMIUM 으로
  고친다~~ → **도달 불가로 확인됨**(2026-08-06). `UpdatePostUseCase` 는 `visibility` 를 받지
  않는다. 남겨 두는 이유는 기록이다 — 이 시나리오를 근거로 범위를 넓히지 말 것
- **`ARTIST_POST` 까지 함께 막는다** — 게이팅이 존재하는 이유를 없앤다
- 🔴 **`VisibilityTierE2ETest` 를 빼먹는다** — 안 A(좁히기)를 고르면 그 테스트가 `FAN_POST` 를
  `PREMIUM`/`MEMBERS_ONLY` 로 발행하므로 **머지 즉시 e2e 가 빨개진다**. 스펙
  (`v1-e2e-scenarios.md` § Scenario 3)도 함께 고쳐야 한다

# Definition of Done

- [x] 결정 + ADR(`ADR-003` ACCEPTED — B) + 구현(B = 계약 명문화 + 가드 2건, 코드 무변경)
- [x] AC-1/AC-2 양방향 — AC-1 = 계약 절 신설, AC-2 = 코드·e2e 무변경을 `git diff` 로 확인
- [x] 계약 문서 정합 + 그 정합을 **지키는** 트리거/가드(AC-4·AC-5, 둘 다 bite 확인)
- [x] Ready for review
