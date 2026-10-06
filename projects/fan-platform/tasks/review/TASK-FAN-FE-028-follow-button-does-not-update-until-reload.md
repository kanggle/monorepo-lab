# Task ID

TASK-FAN-FE-028

# Title

아티스트 팔로우가 200 으로 성공해도 버튼 라벨이 3초 넘게 「팔로우」로 남는다 — 상태 갱신이 서버 액션의 await 완료를 기다린다

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

- 출처: `TASK-MONO-764` 23차 창(데모 기능 점검표, 흐름 9 — 아티스트 팔로우).
- 인접(다른 레이어): `TASK-FAN-FE-017`(DONE) — 그 티켓은 **초기 상태**(서버 컴포넌트가
  SSR 시점에 `initialFollowing` 을 실제 값으로 넘기는가)를 고쳤다. 이 티켓은 **클릭 이후**
  (낙관적 갱신)를 다룬다 — 서로 다른 결함이고, 017 이 이미 고친 것을 되돌리지 않는다.

---

# 배경 — 23차 창 라이브 실측 (2026-10-06 UTC)

`/artists/[id]` 에서 팔로우 버튼을 클릭하면 서버 액션이 **200** 으로 응답하지만, 버튼
텍스트는 **3초 넘게** 「팔로우」로 남아 있다가 새로고침해야 「팔로잉」으로 바뀐다.

코드(`features/follow/ui/FollowButton.tsx:20-35`):

```tsx
const onClick = () => {
  startTransition(async () => {
    try {
      if (following) {
        await unfollowArtist(artistAccountId, artistId);
        setFollowing(false);
      } else {
        await followArtist(artistAccountId, artistId);
        setFollowing(true);          // ← await 완료 후에만 상태가 바뀐다
      }
    } catch { ... }
  });
};
```

`setFollowing` 호출이 **서버 액션의 await 가 끝난 뒤**에만 일어난다 — 즉 라벨 갱신이
서버 왕복 지연(이번 창에서 ≥3초, 콜드스타트/게이트웨이 지연으로 추정) 전체를 그대로
사용자에게 드러낸다. 서버 쪽은 이미 팔로우를 반영했다(피드에 해당 아티스트 글이 뜨는 것
으로 확인) — **버튼만 느리다.**

---

# Goal

팔로우/언팔로우 클릭 후, 서버 요청이 느려도 버튼이 즉시(또는 명확한 진행 표시와 함께)
반응한다 — 조용히 3초 넘게 멈춰 있는 상태가 없다.

---

# Scope

## In Scope

- `FollowButton` 의 클릭 처리 방식을 아래 둘 중 하나로 바꾼다(착수 시 결정 — 둘 다 정당한
  선택이며 이 티켓이 강제하지 않는다):
  1. **낙관적 갱신** — 클릭 즉시 `setFollowing` 을 호출하고, 실패 시 롤백한다.
  2. **명시적 진행 표시 유지** — 라벨은 그대로 두되(또는 "처리 중" 표시) `isPending`
     동안 **눈에 보이는** 로딩 신호를 주어, 사용자가 "안 눌렸나보다"로 오인하지 않게 한다.
- 어느 쪽을 고르든, 실패(스왈로우되지 않는 진짜 오류) 시 UI 가 서버 상태와 불일치하지
  않도록 처리한다.

## Out of Scope

- `TASK-FAN-FE-017` 이 고친 초기 SSR 상태 — 그대로 둔다.
- `FollowButton` 의 빈 `catch`(에러를 사용자에게 알리지 않는 것) — `017` 이 이미 범위
  밖으로 적었고 이번 창에서도 오류가 발화하지 않았다. 추측으로 묶지 않는다.

---

# Acceptance Criteria

- [ ] **AC-0 (재현)** — 클릭→라벨 변경까지의 지연을 다시 측정한다(이번 창은 ≥3초 1회
      관측). ⏳ **오케스트레이터가 다음 창에서 측정** — 라이브 브라우저 재측정은 이
      구현 세션(단위 테스트 환경)에서 수행할 수 없다.
- [x] **AC-1** — 위 Scope 의 두 선택지 중 하나를 고르고 근거를 적는다. → **옵션 1
      (낙관적 갱신)** 선택. 근거: (a) Goal 이 "느려도 즉시 반응"을 요구하고, 옵션 1 이
      가장 직접적으로 그것을 만족한다. (b) 같은 저장소의 `ReactionBar`
      (`features/post/ui/ReactionBar.tsx`)가 이미 같은 패턴(클릭 즉시 로컬 상태 설정,
      서버 액션은 fire-and-forget)을 쓰고 있어 컨벤션과 일치한다. (c) 409/404 는
      `actions.ts` 가 이미 "의도적으로 삼켜" 정상 반환하므로(`TASK-FAN-FE-017` 이 문서화)
      `catch` 에 도달하는 것은 **진짜** 실패뿐이라 롤백 범위가 좁고 안전하다.
- [x] **AC-2** — 실패(네트워크 오류 등 진짜 실패)가 나면 UI 가 서버와 다른 상태를 보여주지
      않는다(낙관적 갱신을 골랐다면 롤백 테스트). → `src/__tests__/follow-button-optimistic.test.tsx`
      "🔴 AC-2 롤백" 케이스: `followArtist` 가 reject 하면 라벨이 클릭 전 상태(「팔로우」·
      `aria-pressed=false`)로 되돌아오는 것을 단언.
- [x] **AC-3** — 단위 테스트: 라벨이 클릭 한 틱 안에 바뀌거나(옵션 1), 요청이 끝날 때까지
      구별 가능한 진행 상태를 유지한다(옵션 2) — "결국 200 이 온다"만으로는 통과하지 않는다.
      → `follow-button-optimistic.test.tsx` 의 두 "AC-3" 케이스가 **아직 resolve 되지 않은**
      deferred promise 를 서버 액션 목으로 주고, 그 상태에서 라벨이 이미 바뀌었음을 단언
      (양방향: 팔로우→팔로잉, 팔로잉→팔로우). 수정 전 코드로 되돌려 돌리면 이 두 케이스가
      빨갛게 실패하는 것으로 bite 확인(아래 "무는가" 참조).
- [x] **AC-4 (회귀)** — `TASK-FAN-FE-017` AC-2(두 아티스트 대조 — 팔로잉/팔로우 라벨이
      갈라지는 것)가 여전히 성립한다. → `src/__tests__/follow-status.test.tsx` (FE-017 이 만든
      그 AC-2 대조군 스위트, 수정 없이 그대로 둠) 4/4 통과 유지 확인 — 이 티켓은 초기
      `initialFollowing` 전달 경로를 건드리지 않았다.

---

# ✅ 구현 결과 (2026-10-06 UTC)

## 선택 (AC-1)

옵션 1(낙관적 갱신). `FollowButton.onClick` 이 `wasFollowing` 을 캡처하고 `setFollowing`
을 **서버 액션 호출 전**에 동기적으로 실행하도록 바꿨다 — 전후 비교는
`git diff`(아래 diff 요약)를 볼 것. 롤백은 `catch` 에서 `setFollowing(wasFollowing)`.

## 무는가 (bite) — 수정 전 코드로 되돌려 확인

`follow-button-optimistic.test.tsx` 를 수정 전 `FollowButton.tsx`(scratch 백업, 저장소에는
반영하지 않음)에 대해 돌리자:

```
Tests  3 failed | 1 passed (4)
```

실패한 3개는 정확히 AC-3 두 케이스(팔로우 방향·언팔로우 방향)와 AC-2 롤백 케이스 —
낙관적 갱신이 없으면 클릭 직후 라벨이 아직 안 바뀐 상태라 `toContain('팔로잉'/'팔로우')`
가 깨진다. 통과한 1개("연타 disabled")는 이 결함과 무관한 축이라 수정 전에도 통과하는
것이 맞다. 수정 코드를 복원한 뒤 재실행하여 4/4 복귀, `tr -d '\r'` 로 정규화한 내용이
원본과 바이트 동일함을 `cmp` 로 확인.

## 게이트

- `pnpm vitest run`(전체 스위트) — rc=0, **42 files / 367 tests 통과**.
- `npx tsc --noEmit` — rc=0.
- `npx next lint` — rc=0 ("No ESLint warnings or errors").

## 범위 밖으로 둔 것

- `FollowButton` 의 빈 `catch`(에러 미표시) — Scope 가 Out of Scope 로 명시. 건드리지 않음.
- `TASK-FAN-FE-017` 이 고친 초기 SSR 상태(`getFollowStatus` 호출부) — 손대지 않음.

---

# Related Specs

- `projects/fan-platform/tasks/done/TASK-FAN-FE-017-artist-detail-follow-button-never-knows-it-is-already-following.md`
  (인접 레이어 — 초기 상태. 이 티켓은 클릭 후 상태.)

# Related Contracts

- `projects/fan-platform/specs/contracts/community-api.md` § Follows

---

# Edge Cases

- 연타(더블클릭) — 현재 `disabled={isPending}` 로 막혀 있다. 수정 후에도 이 가드가
  유지되는지 확인.
- 409(`ALREADY_FOLLOWING`, `017` 이 기록한 "의도적으로 삼키는" 경로) — 그 경로에서도
  라벨이 서버 상태와 맞아야 한다(이미 정상 — 회귀로만 확인).

# Failure Scenarios

- **서버 액션 자체를 빠르게 만드는 것으로 "고친다"** — UX 계약을 안 고친 것이다. 지연이
  재발하면(네트워크·콜드스타트) 이 증상이 다시 나타난다.
- **낙관적 갱신을 적용했는데 실패 롤백을 테스트하지 않는다** — 네트워크 오류 시 사용자가
  "팔로잉" 으로 보이는데 실제로는 아닌 거짓 양성이 생긴다.
