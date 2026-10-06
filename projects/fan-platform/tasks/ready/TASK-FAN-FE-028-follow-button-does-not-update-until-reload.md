# Task ID

TASK-FAN-FE-028

# Title

아티스트 팔로우가 200 으로 성공해도 버튼 라벨이 3초 넘게 「팔로우」로 남는다 — 상태 갱신이 서버 액션의 await 완료를 기다린다

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
      관측).
- [ ] **AC-1** — 위 Scope 의 두 선택지 중 하나를 고르고 근거를 적는다.
- [ ] **AC-2** — 실패(네트워크 오류 등 진짜 실패)가 나면 UI 가 서버와 다른 상태를 보여주지
      않는다(낙관적 갱신을 골랐다면 롤백 테스트).
- [ ] **AC-3** — 단위 테스트: 라벨이 클릭 한 틱 안에 바뀌거나(옵션 1), 요청이 끝날 때까지
      구별 가능한 진행 상태를 유지한다(옵션 2) — "결국 200 이 온다"만으로는 통과하지 않는다.
- [ ] **AC-4 (회귀)** — `TASK-FAN-FE-017` AC-2(두 아티스트 대조 — 팔로잉/팔로우 라벨이
      갈라지는 것)가 여전히 성립한다.

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
