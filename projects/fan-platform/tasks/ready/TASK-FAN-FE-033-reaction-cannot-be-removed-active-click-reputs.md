# Task ID

TASK-FAN-FE-033

# Title

팬이 남긴 반응을 취소할 수 없다 — 눌린 반응을 다시 누르면 같은 타입을 PUT 만 다시 하고, DELETE(`removeReaction`)를 부르는 곳이 없다

# Status

ready

# Owner

fan-platform

# Task Tags

- frontend
- community

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (한 컴포넌트의 분기 + 낙관적 갱신 + 테스트)
> Drafted 2026-10-25 UTC. 출처: 35차 데모 창 — 소유자가 «반응을 누른 상태에서 다시 눌러도 취소되지 않는다» 를 관찰. 사실은 origin/main `1ccccdb88` 에서 읽었다.

---

# 배경

## 현재 코드 상태 (2026-10-25 UTC 실측)

- `projects/fan-platform/web/fan-platform-web/src/features/post/ui/ReactionBar.tsx:31-38` — `onClick(type)` 이 **무조건** `setActive(type)` 후 `setReaction(postId, type)`(PUT) 을 부른다. 눌린 버튼(`active === type`)을 다시 눌러도 같은 타입을 PUT 한다. 백엔드의 PUT 은 `(post_id, reactor_account_id)` 멱등 upsert 라 **아무 일도 일어나지 않는다** — 화면은 계속 눌린 상태.
- `features/post/api/reactions.ts:21-28` `removeReaction(postId)`(DELETE `/api/v1/community/posts/{id}/reactions`) 는 **존재하지만 호출하는 곳이 없다**(dead server action).
- 백엔드 DELETE 는 있다: `ReactionController` remove → 204, 계약 `projects/fan-platform/specs/contracts/http/community-api.md:464-466`. 즉 **프런트만의 결함**이다 — 새 백엔드·계약 변경 없음(AC-0 에서 줄을 다시 확인).
- 관련 선행: `TASK-FAN-FE-029` 가 `initialReaction` 으로 «내 반응이 눌린 채 다시 열림» 을 고쳤다 — 이 티켓은 그 눌린 상태에서 **내리는** 방향이다.

---

# Goal

post 상세 화면에서 팬이 이미 눌러둔 반응을 다시 누르면 반응이 취소되고(눌림 해제 · DELETE 호출), 다른 타입을 누르면 그 타입으로 바뀐다(PUT). 총 반응 수가 서버 값으로 갱신된다.

# Scope

## In Scope

1. **`ReactionBar.onClick` 분기** — `type === active` 이면 `removeReaction(postId)` 를 호출하고 `active` 를 `null` 로(낙관적), 아니면 현재처럼 `setReaction(postId, type)` + `active = type`. `FollowButton`/`CommentPanel` 의 낙관적 갱신·롤백 관용구를 재사용한다.
2. **실패 시 롤백** — 서버 액션이 던지면(또는 `{ok:false}`) 직전 `active` 로 되돌린다. 현재 `void setReaction(...)` fire-and-forget 는 실패를 삼킨다 — 이 티켓은 **새 취소 경로** 에 대해서만 롤백을 요구하고, 기존 PUT 경로는 같은 관용구로 맞추되 동작을 바꾸지 않는다.
3. **총 반응 수 표기** — `총 {totalReactions}개의 반응` 은 서버 렌더 값이다. 서버 액션의 `revalidatePath` 로 갱신되는 기존 방식을 유지한다(취소해도 숫자가 따라 내려가는지 AC-4 에서 본다).

## Out of Scope

- 백엔드·계약 변경 (DELETE 는 이미 있음).
- 피드 카드(`PostCard`)의 반응 수 신선도 — `TASK-FAN-BE-053`.
- 반응 종류 추가/삭제, 반응한 사람 목록.

# Acceptance Criteria

- [ ] **AC-0** — `ReactionBar.tsx:31-38` · `reactions.ts:21-28` · 백엔드 DELETE(`ReactionController` · 계약 `community-api.md:464-466`)를 착수 시점에 다시 읽어 위 «현재 코드 상태» 와 같은지 확인하고 어긋난 줄을 기록한다.
- [ ] **AC-1 (취소, 수정 전 빨강)** — 단위 테스트: `initialReaction='LIKE'` 로 렌더한 `ReactionBar` 에서 «좋아요» 를 클릭하면 `removeReaction(postId)` 가 1회 호출되고 `setReaction` 은 호출되지 않으며, 클릭 직후 «좋아요» 의 `aria-pressed` 가 `false`. **수정 전 코드에서 이 테스트가 빨강**임을 확인(되돌려 실패 메시지를 구현 보고에 적는다).
- [ ] **AC-2 (전환)** — `initialReaction='LIKE'` 에서 «사랑해요» 클릭 → `setReaction(postId,'LOVE')` 1회 · `removeReaction` 미호출 · «사랑해요» 만 `aria-pressed=true`.
- [ ] **AC-3 (첫 반응, 대조군)** — `initialReaction=null` 에서 클릭 → `setReaction` 1회 · `removeReaction` 미호출 (기존 동작 유지).
- [ ] **AC-4 (롤백)** — `removeReaction` 이 reject 하면 `aria-pressed` 가 다시 `true` (눌림 복원). 서버 액션 mock 이 실패하도록 한 테스트로 확인.
- [ ] **AC-5 (회귀)** — 기존 `src/__tests__/reaction-restore.test.tsx` 와 post 관련 스위트 · `tsc --noEmit` · `next lint` 초록.
- [ ] **AC-6 (라이브, 다음 데모 창)** — 로그인한 팬이 반응이 눌린 글에서 같은 반응을 다시 눌러 **1초 안에 눌림이 풀리고**, 새로고침 후에도 풀려 있으며 `총 N개` 가 1 줄어든다. 다른 반응을 누르면 바뀐다. 못 재면 ⚪ + 사유. (Vercel 배포만으로 충분 — 굽기 불필요.)

# Related Specs

- `projects/fan-platform/specs/services/fan-platform-web/overview.md`
- `projects/fan-platform/specs/services/fan-platform-web/architecture.md` § Package Layout

# Related Contracts

- `projects/fan-platform/specs/contracts/http/community-api.md` § Reactions — `PUT`(upsert) · `DELETE`(204). **변경 없음.**

# Edge Cases

- 두 번 빠르게 클릭 — `isPending` 동안 버튼이 `disabled` 이므로 중복 호출 없음(기존 보장 유지, 테스트로 확인).
- 이미 서버에 반응이 없는데 화면이 눌려 있는 경우(다른 탭에서 취소) — 계약(`community-api.md:466`)상 DELETE 는 «반응이 없어도 no-op, 204» 이므로 정상 취소로 취급해 눌림을 푼다 — 별도 분기 불필요(AC-0 에서 확인).
- 로그아웃 상태 — `ReactionBar` 는 멤버 상세 경로에만 있다(익명 zero-gateway 경로에는 없음). 변경 없음.

# Failure Scenarios

1. `active` 만 `null` 로 만들고 `removeReaction` 을 부르지 않는다 — 화면은 풀리지만 새로고침하면 다시 눌려 있다(AC-1 이 호출 단언을 요구하는 이유; AC-6 이 새로고침 후 상태를 본다).
2. 취소 요청이 실패했는데 화면은 풀린 채 남는다 — 서버와 화면이 갈라진다(AC-4).
3. 취소 후 `총 N개` 가 안 줄어든다 — `removeReaction` 의 `revalidatePath` 누락/무효 (AC-6 에서 숫자를 함께 본다).
