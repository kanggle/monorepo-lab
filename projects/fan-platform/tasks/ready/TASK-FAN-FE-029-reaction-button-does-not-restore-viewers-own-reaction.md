# Task ID

TASK-FAN-FE-029

# Title

반응(리액션) 버튼이 저장은 되는데 새로고침 뒤 내가 누른 반응을 기억하지 못한다 — `ReactionBar` 가 서버의 "내 반응" 을 받지 않는다

# Status

ready

# Owner

fan-platform

# Task Tags

- frontend
- community
- bug

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet.

---

# Dependency Markers

- 출처: `TASK-MONO-764` 23차 창(데모 기능 점검표, 흐름 11 — 글 쓰기/반응).
- 참고(같은 모양, 복사할 패턴): `TASK-FAN-FE-017`(DONE) — 팔로우 버튼의 "초기 상태를
  서버에서 안 읍는다" 결함과 **같은 클래스**다. AC-0b(계약 게이트)·AC-2(대조군)·AC-4
  (호출부 축 테스트)의 구조를 그대로 가져온다.

---

# 배경 — 23차 창 라이브 실측 (2026-10-06 UTC)

`/posts/[id]` 에서 반응(👍 좋아요 / ❤️ 사랑해요)을 누르면 200 이 오고 카운트가 유지된다
(피드에 「반응 1」로 보임 — **서버 쪽 저장은 정상**). 그런데 새로고침하면 그 버튼의
`aria-pressed` 가 `false` 다 — **내가 누른 반응이 복원되지 않는다.**

코드:

```tsx
// features/post/ui/ReactionBar.tsx
export function ReactionBar({ postId, totalReactions }: { postId: string; totalReactions: number }) {
  const [active, setActive] = useState<ReactionType | null>(null);   // ← 항상 null 로 시작
  ...
}
```

호출부:

```tsx
// features/post/ui/memberPostDetail.tsx:85
<ReactionBar postId={post.postId} totalReactions={post.reactionCount} />
```

`ReactionBar` 는 `postId` 와 `totalReactions`(집계)만 받는다 — **뷰어 자신의 기존 반응**을
전달하는 prop 이 아예 없다. `setReaction`/`removeReaction`(`features/post/api/reactions.ts`)
은 PUT/DELETE 뒤 `revalidatePath` 로 **카운트** 는 서버에서 다시 읍지만, 뷰어 전용 상태는
애초에 질문된 적이 없다.

---

# Goal

글 상세를 새로고침해도, 뷰어가 이미 누른 반응이 해당 버튼의 `aria-pressed=true` 로
복원된다.

---

# Scope

## In Scope

- 먼저 계약을 읍는다 — `community-api.md` § Reactions 에 **뷰어별 반응 조회** 경로가
  있는가(글 상세 응답에 `myReaction` 류 필드로 포함돼 있을 수도 있다). `TASK-FAN-FE-017`
  의 AC-0b 처럼, 없으면 **HARDSTOP-08** 이고 먼저 계약/백엔드 조회 경로가 선행 티켓이다.
- 있으면, `memberPostDetail.tsx` 가 그 값을 `post` 객체(이미 서버에서 가져온 것)에서 읍어
  `ReactionBar` 에 초기값으로 넘기고, `ReactionBar` 의 `active` 가 그 값으로 초기화된다
  (`017` 이 `initialFollowing` 을 넘긴 것과 같은 모양).

## Out of Scope

- 반응 집계(카운트) 로직 — 이미 정상.
- 반응 종류 추가/변경(이모지 세트) — 새 기능이 아니라 이 결함의 수정.

---

# Acceptance Criteria

- [ ] **AC-0 (전제 확인)** — `active` 가 여전히 항상 `null` 로 시작하는지, 호출부가
      여전히 뷰어 반응을 안 넘기는지 확인한다.
- [ ] **AC-0b (선행 게이트)** — 뷰어별 반응을 읍는 경로(단건 또는 글 상세에 포함)가
      계약에 있는지 확인한다. 없으면 STOP — 선행 티켓이 필요하다(`TASK-FAN-BE-049` 가
      팔로우 조회에 했던 역할과 같다).
- [ ] **AC-1** — `memberPostDetail.tsx` 가 서버에서 읍은 뷰어의 기존 반응을 `ReactionBar`
      에 초기값으로 넘긴다. 상수 `null` 이 남아 있으면 미달.
- [ ] **AC-2 (대조군)** — **두 글**로 확인한다: 뷰어가 반응한 글은 새로고침 뒤에도 해당
      버튼이 `aria-pressed=true`, 반응하지 않은 글은 전부 `false`. 한쪽만 보면 지금도
      "맞게" 보인다(`017` Failure Scenario 와 동일한 함정).
- [ ] **AC-3** — 반응을 바꾼 뒤(예: 👍→❤️) 새로고침하면 **새 반응만** `aria-pressed=true`
      다(이전 반응은 false로 돌아간다).
- [ ] **AC-4** — 유닛 테스트가 **호출부 축**까지 단언한다(컴포넌트만 테스트하면 원래
      결함이 초록으로 통과한다 — `017` AC-4 가 겪은 것과 같은 함정).

---

# Related Specs

- `projects/fan-platform/specs/contracts/community-api.md` § Reactions
- `projects/fan-platform/tasks/done/TASK-FAN-FE-017-artist-detail-follow-button-never-knows-it-is-already-following.md`
  (같은 클래스의 선행 수정 — 구조를 복사한다)

# Related Contracts

- `projects/fan-platform/specs/contracts/community-api.md` § Reactions — 뷰어별 조회
  경로의 유무가 이 티켓의 갈림길이다(위 AC-0b).

---

# Edge Cases

- 비로그인 뷰어 — 반응 자체가 로그인 상태만 가능하다면(미들웨어 보호 여부 확인) 이 경로는
  항상 로그인 상태만 도달한다.
- 글이 삭제/숨김 전환된 뒤의 과거 반응 — 범위 밖(현재 동작 유지).

# Failure Scenarios

- **한 글로만 검증한다** — 반응 안 한 글만 골랐다면 지금 상태로도 "통과"처럼 보인다.
- **집계값에서 뷰어 반응을 추론하려 한다** — 집계는 뷰어 귀속 정보를 담지 않는다(불가능).
- **컴포넌트 기본값만 바꾼다** — 방향만 바뀐 같은 결함(모든 글이 `true` 로 보이게 됨).
