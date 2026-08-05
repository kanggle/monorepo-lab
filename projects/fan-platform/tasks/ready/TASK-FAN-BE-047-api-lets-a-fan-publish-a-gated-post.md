# Task ID

TASK-FAN-BE-047

# Title

API 는 팬이 유료 글을 쓰는 것을 허용한다 — `PublishPostUseCase` 가 `postType` 만 검사하고 `visibility` 는 아무도 검사하지 않는다

# Status

ready

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
- 티어는 **아티스트 수익화**를 위해 존재한다(`architecture.md` § Visibility Tiers).

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

# 🔴 착수 전에 반드시 확인할 것

- [ ] **기존 데이터** — 이미 만들어진 비-PUBLIC `FAN_POST` 행이 있는가?
      (2026-08-06 데모 DB 실측: `FAN_POST` 2건 모두 PUBLIC. 다른 환경은 다시 셀 것)
- [ ] **`UpdatePostUseCase` 로 나중에 바꿀 수 있는가** — 발행만 막고 수정이 열려 있으면
      규칙이 아니라 속도 방지턱이다
- [ ] **운영자/아티스트가 팬 글을 대신 만드는 경로가 있는가** — 있으면 그 경로의 기대값도
      정해야 한다
- [ ] 계약을 좁히는 변경이므로 **ADR 이 필요한지** `platform/architecture-decision-rule.md`
      기준으로 판단할 것. 기존 티어 정의를 **적용**하는 것이면 드리프트 교정이고,
      새 제약을 **도입**하는 것이면 ADR 이다

# Acceptance Criteria

- [ ] **AC-0 (재측정)** — 위 네 항목을 실측한다. 특히 `PublishPostUseCase` 가 그때도
      `visibility` 를 안 보는지 다시 읽는다
- [ ] **AC-1** — `FAN_POST` + 비-PUBLIC 발행 시도가 명시적으로 거절되거나(400/403),
      허용하기로 했다면 **그 결정과 근거가 계약 문서에 적혀 있다**
- [ ] **AC-2 (음성 대조)** — `ARTIST_POST` 의 비-PUBLIC 발행은 **여전히 된다**.
      게이팅 자체를 없애면 아티스트 수익화가 죽는다
- [ ] **AC-3** — 계약 문서와 실제가 일치한다

# Related Specs

- `apps/community-service/.../application/PublishPostUseCase.java`
- `specs/contracts/http/community-api.md` § `POST /api/community/posts`
- `specs/services/community-service/architecture.md` § Visibility Tiers

# Edge Cases

- 이미 존재하는 비-PUBLIC `FAN_POST` — 거절 규칙을 넣으면 그 행은 읽기는 되지만 수정이
  막힐 수 있다

# Failure Scenarios

- **발행만 막고 `UpdatePostUseCase` 를 빼먹는다** — 팬이 PUBLIC 으로 만든 뒤 PREMIUM 으로
  고친다
- **`ARTIST_POST` 까지 함께 막는다** — 게이팅이 존재하는 이유를 없앤다

# Definition of Done

- [ ] 결정 + (필요시 ADR) + 구현
- [ ] AC-1/AC-2 양방향 테스트
- [ ] 계약 문서 정합
- [ ] Ready for review
