# Task ID

TASK-FAN-FE-029

# Title

반응(리액션) 버튼이 저장은 되는데 새로고침 뒤 내가 누른 반응을 기억하지 못한다 — `ReactionBar` 가 서버의 "내 반응" 을 받지 않는다

# Status

review

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

> ⛔ **이 갈림길은 이미 밟았다 — 없다 (2026-10-06 UTC 게이트 수행, HARDSTOP-08 발동).**
>
> | 축 | 결과 |
> |---|---|
> | `community-api.md` § Reactions | `PUT /api/community/posts/{postId}/reactions` · `DELETE …` — **끝.** 응답은 방금 호출한 액션의 결과일 뿐, 그 외 시점에 "지금 내 반응이 뭔가"를 물을 길이 없다 |
> | `GET /api/community/posts/{id}` 응답 (§ Posts) | `commentCount`·`reactionCount`(집계)만. 뷰어별 필드 없음 |
> | `ReactionController` (코드) | `@PutMapping` + `@DeleteMapping` — **`@GetMapping` 없음** |
> | `GetPostUseCase` (코드) | `reactionRepository.countByPostId(...)` 만 호출 — `reactionRepository.find(postId, actor.accountId(), tenantId)` 를 **부르지 않는다**. 이 메서드 자체는 이미 존재하고 `AddReactionUseCase`/`RemoveReactionUseCase` 가 멱등 upsert 판정용으로 내부적으로만 쓴다 — 데이터는 테이블에 있고, 질문하는 코드만 없다 |
> | `PostView`/`PostResponse` DTO | 뷰어별 반응 필드 **없음** |
>
> **계약과 코드가 일치한다** ⇒ 드리프트가 아니라 **진짜 공백**이다(`TASK-FAN-FE-017`이
> 팔로우 쪽에서 겪은 것과 같은 클래스). `reactionCount` 집계로 추론하는 우회는 **금지**다 —
> 합계는 귀속 정보를 버리므로 원리적으로 불가능하다(아래 Failure Scenarios 참조).
>
> ⇒ **선행 티켓 `TASK-FAN-BE-051` 을 세웠다**(계약 + community-service 읍기 경로).
> **이 티켓은 그것이 머지된 뒤에 착수한다.** 지금 착수하면 계약 없는 필드를 추측으로
> 넣거나(HARDSTOP-08이 정확히 금지하는 것), 위의 금지된 우회를 하게 된다.

> ✅ **게이트 해소 (2026-10-07 UTC)** — `TASK-FAN-BE-051` 이 PR #4185 로 머지됐다.
> `GET /api/community/posts/{id}` 가 이제 `myReaction: ReactionType | null` 을 낸다
> (`community-api.md` § Posts › `myReaction`). 이 티켓은 그 계약을 그대로 소비한다 —
> 새 조회 경로를 만들지 않는다.

---

# Acceptance Criteria

- [x] **AC-0 (전제 확인)** — 착수 시 `active` 가 여전히 항상 `null` 로 시작했고, 호출부
      (`memberPostDetail.tsx`)가 여전히 `ReactionBar` 에 뷰어 반응을 안 넘기는 상태였음을
      확인했다(아래 "실행 결과" 참조).
- [x] **AC-0b (선행 게이트)** — `TASK-FAN-BE-051` 이 PR #4185 로 머지됐고, `community-api.md`
      § Posts › `myReaction` 에 `GET /api/community/posts/{id}` 응답의 뷰어별 반응 필드가
      명세돼 있다(§ 위 "게이트 해소" 참조).
- [x] **AC-1** — `memberPostDetail.tsx` 가 `post.myReaction ?? null` 을 `ReactionBar` 의
      `initialReaction` prop 으로 넘긴다. 상수 리터럴 없음.
- [ ] ⚪ **AC-2 (대조군)** — **보류, 사유 기록.** 단위 테스트(`reaction-restore.test.tsx`)는
      이 정확한 대조(반응한 글 vs 안 한 글, 두 칸이 갈라지는지)를 목(mock) 응답으로 검증해
      통과했다 — 아래 "무는가" 참조. 그러나 이 AC 가 요구하는 **실제 배포 환경에서의**
      측정은, 23차 창 실측이 적어 둔 대로 현재 데모 백엔드가 `myReaction` 필드를 아직
      보내지 않는 구세대라 지금 이 창에서 불가능하다(`community-api.md` 의 "AMI rebake
      gap" 참조). 오케스트레이터가 `TASK-FAN-BE-051` 을 실은 재굽기 창 이후 라이브로
      재측정해야 닫힌다.
- [x] **AC-3** — 단위 테스트가 반응을 바꾼 경우(`LIKE`→`LOVE`) 새 반응만 `aria-pressed=true`
      이고 이전 반응은 `false` 임을 단언한다(목 응답 기반 — 라이브 재측정은 AC-2 와 같은
      사유로 보류).
- [x] **AC-4** — `reaction-restore.test.tsx` 가 **호출부 축**(`memberPostDetail` 을 직접
      `await` 해 `getPost` 목을 통해 렌더)까지 단언한다. 결함을 되돌려 bite 확인함
      (아래 "무는가" 참조) — 컴포넌트 축 테스트만으로는 원래 결함이 초록으로 통과했을
      칸이다.

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

---

# ✅ 실행 결과 (2026-10-07 UTC)

## 변경

- `entities/post/types.ts` — `Post.myReaction?: ReactionType | null` 추가. `mediaRefs` 와
  같은 convention(옵셔널 + AMI rebake gap 주석) 그대로.
- `features/post/ui/ReactionBar.tsx` — `initialReaction?: ReactionType | null = null` prop
  추가, `useState(initialReaction)` 으로 초기화. **낙관적 토글 동작은 그대로** — `onClick`
  로직 변경 없음.
- `features/post/ui/memberPostDetail.tsx` — `<ReactionBar ... initialReaction={post.myReaction
  ?? null} />`. `017` 이 `initialFollowing` 을 넘긴 것과 같은 모양.
- 새 테스트 `src/__tests__/reaction-restore.test.tsx` — 2축(컴포넌트/호출부) × 두 대조군 +
  필드부재 안전망.

## 🔴 컴포넌트는 거의 손대지 않았다 — 결함은 호출부에 있었다

`017` 의 예측과 같은 모양: `ReactionBar` 는 prop 을 받아 그대로 그리는 한 줄만 바뀌었고,
실제 결함은 `memberPostDetail.tsx` 가 상수를 넘긴 것(또는 아예 안 넘긴 것)이었다.

## AC 판정

| AC | 판정 | 근거 |
|---|---|---|
| AC-0 | ✅ | 착수 시 `useState<ReactionType \| null>(null)` 하드코딩과 호출부의 2-prop 호출 확인 |
| AC-0b | ✅ | `TASK-FAN-BE-051`(PR #4185) 머지 확인 — `community-api.md` § Posts › `myReaction` |
| AC-1 | ✅ | `post.myReaction ?? null` → `initialReaction`. 상수 리터럴 없음 |
| AC-2 | ⚪ 보류 | 목 기반 대조(두 글)는 테스트로 통과 — 라이브 측정은 데모 백엔드의 AMI 재굽기 뒤로 |
| AC-3 | ✅(목 기반) | `reaction-restore.test.tsx` 의 "반응을 바꾼 뒤" 케이스 |
| AC-4 | ✅ | 호출부 축 — 아래 "무는가" |

## 🔴 무는가 — 원래 결함을 되돌려 확인했다 (bite)

`memberPostDetail.tsx` 의 `<ReactionBar initialReaction={post.myReaction ?? null} />` 를
원래 결함 모양(`<ReactionBar postId={post.postId} totalReactions={post.reactionCount} />`,
prop 자체를 안 넘김)으로 **되돌려** `reaction-restore.test.tsx` 를 돌렸다:

```
Test Files  1 failed (1)
     Tests  2 failed | 4 passed (6)
```

정확히 **호출부 축의 두 칸**(AC-1/AC-2 셀, AC-3 셀)만 빨개졌다 — `expected 'false' to be
'true'`. 나머지 넷(컴포넌트 축 2개, AC-2 음성 대조, 필드부재 안전망)은 상수 `null` 로도
통과하는 칸들이다(결함을 안 가린다는 뜻) ⇒ 실패한 두 칸에만 이빨이 있다. 되돌린 파일을
원복한 뒤 `cmp` 로 수정본과 바이트 동일함을 확인하고 재실행해 6/6 통과를 재확인했다.

## 로컬 검증

- `pnpm install --frozen-lockfile` (프로젝트 루트) — rc=0
- `npx vitest run` (전체) — **46 파일 / 407 테스트 전부 통과**, rc=0
- `npx tsc --noEmit` — rc=0, 출력 없음
- `npx next lint` — rc=0, "No ESLint warnings or errors"

## 범위 밖으로 두고 온 것

- AC-2 라이브 재측정 — 위 표 참조. 오케스트레이터가 재굽기 창에서 측정.
- 반응 집계/종류 변경 — Out of Scope 그대로.
