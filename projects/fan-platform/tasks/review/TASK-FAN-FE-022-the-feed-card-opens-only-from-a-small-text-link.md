# Task ID

TASK-FAN-FE-022

# Title

피드 카드는 오른쪽 아래 작은 «자세히 보기 →» 글자로만 열린다 — **카드 어디를 눌러도** 상세로 가게 한다

# Status

review

# Owner

frontend

# Task Tags

- code
- test

---

# Goal

소유자 요청(2026-09-15): *"피드에서 자세히 보기→로 상세 들어가는 것에서, 카드 영역을 누르면 들어가는 것으로 수정."*

지금 피드 카드에서 상세(`/posts/{id}`)로 가는 길은 footer 오른쪽의 **작은 텍스트 링크 하나**뿐이다. 카드에 사진이 생기면서
(`TASK-MONO-678` 공개 · `TASK-MONO-679` 회원) 방문자가 **사진이나 제목을 누르는 것**이 자연스러운 동작이 됐는데, 그 자리는 눌러도
아무 일도 없다. 카드 전체가 상세로 가는 링크가 되게 한다.

# 선행 · 순서

- 🔴 **선행: `TASK-MONO-679` (PR #3818) 머지.** 그 PR 이 회원 카드 `features/post/ui/PostCard.tsx` 를 고친다(사진 추가). 같은 파일을
  병렬로 고치면 머지 충돌이 확실하다 — 공유 파일 연작은 **직렬**로 간다. 이 티켓의 구현 브랜치는 679 머지 **뒤의** `main` 에서 딴다.

---

# Context — 실측 (2026-09-15 UTC, `main` = `54265509c`)

| 카드 | 파일 | 쓰는 화면 | 상세로 가는 길 | 카드 안의 다른 링크 |
|---|---|---|---|---|
| 공개 카드 | `features/public-browse/ui/PublicPostCard.tsx` | `/` 공개 피드 · `/artists/[id]` 글 목록(`PublicFeedList`) | footer `자세히 보기 →` | 아티스트 이름 배지 → `/artists/{id}` · 🔴 잠긴 카드의 **「멤버십 안내 보기」** → `/membership` |
| 회원 카드 | `features/post/ui/PostCard.tsx` | `/` 팔로우 피드(`FeedList`) | footer `자세히 보기 →` | 없음 |

- `자세히 보기` 문구를 **누르거나 찾는** 시험·e2e 는 저장소에 **0건**(두 컴포넌트 안에만 있다).

---

# Scope

## 포함

- `projects/fan-platform/web/fan-platform-web/src/features/public-browse/ui/PublicPostCard.tsx`
- `projects/fan-platform/web/fan-platform-web/src/features/post/ui/PostCard.tsx`
- 두 카드의 시험(`features/public-browse/__tests__/`, `src/__tests__/post-card.test.tsx`)

## 제외

- 상세 화면 자체 · 링크 목적지(`/posts/{id}`)는 그대로
- 아티스트 목록 카드(`PublicArtistCard`) — 요청 범위는 **피드**다

---

# Acceptance Criteria

## AC-0 — 착수 전 실측

- [ ] 679 가 머지됐는지 확인하고, 두 카드의 **현재** 구조(카드 안 링크 목록)를 위 표와 다시 대조한다.

## AC-1 — 카드 어디를 눌러도 상세로 간다

- [ ] 두 카드 모두 카드 영역(사진 · 제목 · 본문 미리보기 · 빈 여백) 클릭이 `/posts/{id}` 로 간다. 잠긴 카드도 간다(상세가 게이트를 보여 준다).
- [ ] 🔴🔴 **`<a>` 안에 `<a>` 를 넣지 않는다** — HTML 규칙 위반이고 브라우저가 DOM 을 고쳐 쓰면서 링크가 깨진다. 카드를 통째로 `<Link>` 로
      감싸는 방식은 **안 된다**(카드 안에 이미 링크가 있다). 권장: 카드 `relative` + 상세 링크 하나를 `after:absolute after:inset-0` 으로
      카드 전체에 늘리는 **stretched link**.
- [ ] 🔴 카드 안의 **다른 링크는 자기 목적지로 간다** — 아티스트 배지 → `/artists/{id}`, 잠긴 공개 카드의 「멤버십 안내 보기」 → `/membership`.
      (stretched link 를 쓰면 이 둘은 `relative z-10` 등으로 위에 올려야 한다.)
- [ ] 「자세히 보기 →」 텍스트 링크는 없앤다(카드 자체가 그 역할을 한다).

## AC-2 — 키보드 · 보조기기

- [ ] 카드 하나당 상세로 가는 탭 정지는 **하나**다. 그 링크에 초점이 가면 **카드 전체**에 초점 표시가 보인다(`focus-visible`).
- [ ] 링크의 접근 가능한 이름은 글 제목이다. 🔴 회원 카드의 잠긴 항목은 `title` 이 `null` 이다 — 그때도 이름이 비지 않는다(예: «멤버십 전용 포스트»).

## AC-3 — 시험

- [ ] 두 카드 각각: 상세로 가는 `<a href="/posts/{id}">` 가 **정확히 하나** · `a a`(중첩 링크) **0** · 「자세히 보기」 문구 **0**.
- [ ] 🔴 다른 링크가 살아 있다: 아티스트 배지 href, 잠긴 공개 카드의 `/membership` href — **대조군**으로 둘 다 단언한다.
- [ ] 잠긴 회원 카드(`title: null`)의 상세 링크 접근 가능한 이름이 비어 있지 않다.
- [ ] 🔵 jsdom 은 CSS 로 늘린 클릭 영역을 재지 못한다 ⇒ «사진을 눌렀더니 상세로 갔다» 는 **실제 브라우저**로 잰다(아래 AC-4).

## AC-4 — 라이브

- [ ] 머지·배포 뒤 `fan.hubwang.com/` 에서 공개 카드의 **사진 영역**을 눌러 `/posts/{id}` 로 가는지, **아티스트 배지**를 눌러
      `/artists/{id}` 로 가는지 헤드리스 브라우저로 잰다. 🔴 배포 판정은 `build-info.json` 의 `commit` 으로 한다(워크플로 성공 ≠ 배포).
- [ ] 게이트: `vitest` · `tsc` · `next lint` · `next build`.

---

# Related Specs / Contracts

- `projects/fan-platform/specs/services/fan-platform-web/architecture.md` — 화면 구성
- 선행: `TASK-MONO-679`(회원 카드 사진) · 배경: `TASK-MONO-678`(공개 카드 사진)
- 계약 변경 없음

---

# Edge Cases

| 상황 | 기대 |
|---|---|
| 아티스트 배지 클릭 | `/artists/{id}` (상세 아님) |
| 잠긴 공개 카드의 「멤버십 안내 보기」 클릭 | `/membership` (상세 아님) |
| 잠긴 카드의 그 밖 영역 클릭 | `/posts/{id}` — 상세가 게이트를 보여 준다 |
| 회원 카드 잠긴 항목(`title: null`) | 링크 이름이 비지 않는다 |
| 카드 본문 텍스트를 드래그로 선택 | 🔵 stretched link 위라 선택이 어렵다 — 피드 카드에서는 받아들인다(상세에서 선택 가능) |
| 새 탭으로 열기(⌘/Ctrl-클릭 · 가운데 클릭) | 진짜 `<a>` 라 그대로 동작한다 — `onClick` + `router.push` 로 구현하면 **이것이 깨진다** |

---

# Failure Scenarios

1. **카드 전체를 `<Link>` 로 감싼다** → 아티스트 배지·멤버십 링크가 `<a>` 안의 `<a>` 가 되어 HTML 이 깨지고, 브라우저에 따라 클릭이 엉뚱한 곳으로 간다.
2. **`<article onClick={() => router.push(...)}>` 로 한다** → 키보드로 못 열고, 새 탭 열기가 안 되고, 카드 안 링크 클릭이 **두 번 이동**을 일으킨다. 이 카드들은 서버 컴포넌트라 클라이언트 경계도 새로 생긴다.
3. **stretched link 를 깔고 안쪽 링크를 안 올린다** → 아티스트 배지를 눌러도 상세로 간다. 시험은 href 만 보므로 **초록인 채** 이 결함이 나간다 ⇒ AC-4 의 실제 브라우저 확인이 필요한 이유.
4. **잠긴 회원 카드의 링크 이름이 빈다** → 스크린리더가 «링크» 라고만 읽는다.

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Sonnet** (두 컴포넌트의 마크업 + 시험. 설계는 이 티켓에 내려져 있다 — stretched link · 안쪽 링크 올리기 · 탭 정지 하나).

---

# Verification — 구현 세션 (2026-09-15 UTC)

## AC-0 — 착수 전 실측

- 선행 `TASK-MONO-679` 머지 확인: #3818 `MERGED` · squash `2c3c494b0` = 이 브랜치의 분기점.
- 두 카드의 **현재** 링크 목록을 다시 셌다 — 위 표와 같다. 공개 카드: 아티스트 배지 · 잠긴 카드의 「멤버십 안내 보기」 · footer 「자세히 보기 →」. 회원 카드: footer 「자세히 보기 →」 하나.
- 🔴🔴 **설계 중 발견 — 티켓의 권장안만으로는 사진 영역이 안 열린다.** 카드 안 사진 틀(`shared/ui/PostImage`)은 `relative` 이고
  DOM 에서 제목 링크보다 **뒤**에 온다 ⇒ z-index 없는 `::after` 덮개보다 **위에** 그려진다 ⇒ 소유자가 가장 먼저 누를 **사진**을 눌러도
  상세로 안 간다. 덮개에 `after:z-[1]`, 안쪽 링크에 `z-10` 으로 층을 정했다(`shared/ui/cardLink.ts` 머리말).

## AC-1 — 카드 어디를 눌러도 상세로

- 공통 클래스를 **한 곳**(`shared/ui/cardLink.ts`: `CARD_LINK_CLASS` · `CARD_INNER_LINK_CLASS`)에 두고 두 카드가 쓴다 — 두 벌이면 한쪽만 고쳐진다.
- 공개 카드: 제목 `<a>` 가 상세 링크(덮개), 아티스트 배지·「멤버십 안내 보기」에 `relative z-10`, footer 제거.
- 회원 카드: 제목이 보이면 제목이 링크, 🔴 없으면(잠긴 항목 `title: null`) 화면에 안 보이는 이름의 링크 하나, footer 는 댓글·반응 수만.
- 중첩 `<a>` 없음 · `onClick`/`router.push` 없음(진짜 `<a>` 라 새 탭 열기가 그대로 된다).

## AC-2 — 키보드 · 보조기기

- 카드당 상세 탭 정지 하나. 초점 표시는 링크의 `::after` 에 `focus-visible` 링 — 덮개가 카드 크기라 **카드 테두리**로 보인다.
- 링크 이름 = 글 제목 · 잠긴 회원 카드 = «멤버십이 필요한 포스트» · 제목 없는 열린 항목 = «포스트 보기».

## AC-3 — 시험

| 파일 | 칸 |
|---|---|
| `features/public-browse/__tests__/card-click.test.tsx` (신규) | 상세 링크 정확히 1 + 이름=제목 + 덮개 클래스 + 루트 `relative` · 중첩 0·「자세히 보기」 0(열림/잠김) · 🔵 대조군 아티스트 배지 href + `z-10` · 잠긴 카드 `/membership` + `z-10` |
| `src/__tests__/post-card.test.tsx` (+4) | 제목 링크 1 + 덮개 · 🔴 잠긴 항목 이름 «멤버십이 필요한 포스트» · 제목 없는 열린 항목 «포스트 보기» · 중첩 0·「자세히 보기」 0·댓글/반응 수 유지 |

🔵 jsdom 은 CSS 로 늘린 클릭 영역과 z-index 를 **못 잰다** — 위 칸이 재는 것은 «그렇게 짜였는가» 까지다.

## 게이트 (각각 독립 실행 · 명시 rc)

| 게이트 | 결과 |
|---|---|
| `tsc --noEmit` | ✅ rc=0 |
| `next lint --dir src` | ✅ 경고·오류 0 |
| `vitest run` (단독) | ✅ **33 files · 284/284** |
| `next build` | ✅ rc=0 |
| 필수 가드 3종(스테이지 후) | ✅ rc=0 |

## ⚪ 미측정 — 머지·배포 뒤 (AC-4)

라이브 `fan.hubwang.com/` 에서 헤드리스 브라우저로 **실제 클릭**: 사진 영역 → `/posts/{id}` · 아티스트 배지 → `/artists/{id}` · 잠긴 카드의
「멤버십 안내 보기」 → `/membership`. 🔴 이 셋이 이 티켓의 진짜 판정이다(Failure 3: 안쪽 링크를 안 올려도 href 만 보는 시험은 초록).
배포 판정은 `build-info.json` 의 `commit`. close chore 에서 기록한다.
