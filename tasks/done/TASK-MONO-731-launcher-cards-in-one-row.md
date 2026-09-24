# Task ID

TASK-MONO-731

# Status

done (2026-09-24 UTC)

# Title

론처 카드 세 개(이커머스 스토어 · 팬 플랫폼 · Platform Console)를 데스크톱에서 **가로 한 줄**로 나열

# Owner

monorepo

# Task Tags

- demo
- launcher

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 — CSS 미디어쿼리 한 블록.

---

# Goal

소유자 요청(2026-09-24): 포트폴리오 론처(`infra/demo/aws/site/index.html`)에서 세 카드가 세로로 나열되는 것을
가로로 나열한다.

원인: `#surfaces` 는 이미 데스크톱 3열 그리드였지만, `TASK-MONO-729` (d) 가 넣은 구획 제목 `.sgroup`
(「서비스 화면」·「운영 도구」)이 `grid-column: 1 / -1` 로 **줄 전체**를 차지해 콘솔 카드가 다음 줄로 밀렸다
(스토어·팬 한 줄 + 콘솔 한 줄).

# Scope

## In
- 860px 이상에서 `.sgroup` 을 열 머리로 옮긴다: 「서비스 화면」= 1·2열, 「운영 도구」= 3열(1행), 카드 = 2행.
- 729 (d) 의 구분(콘솔은 업무 도메인이 아니라 운영 도구)은 열 머리 + 점선 테두리로 유지.

## Out
- 모바일(<860px) 레이아웃 — 1열 세로 그대로.
- DOM 순서 · 판정 속성(`data-surface` · `data-bundle` · `data-served`) · 문구 — 변경 없음.

# Acceptance Criteria

- [x] **AC-1** — 1280px 에서 세 카드가 한 줄(스크린샷).
- [x] **AC-2** — 400px 에서 기존과 같이 세로 1열(스크린샷).
- [x] **AC-3** — `bash infra/demo/verify-demo-wrapper.sh`(정적) rc=0 — CSS 만 바꿨으므로 판정 가드 무영향 확인.
- [x] **AC-4** — 필수 가드 3종 rc=0(스테이지 후).

# Related Specs

- `tasks/review/TASK-MONO-729-launcher-status-and-visitor-stop-control.md` § (d)

# Related Contracts

- 없음.

# Edge Cases

| 상황 | 기대 |
|---|---|
| 카드가 넷 이상으로 늘어남 | `#surfaces > .scard { grid-row: 2 }` 가 한 줄에 강제 배치한다 — 3열을 넘으면 암시적 열이 생긴다. 카드 추가 시 이 블록을 다시 볼 것 |
| 860px 경계 | 미만은 1열 + 줄 전체 구획 제목(기존 규칙) |

# Failure Scenarios

1. 구획 제목을 지워서 한 줄로 만든다 → 729 (d) 의 콘솔 구분이 사라진다. 제목은 열 머리로 남긴다.

---

# 구현 기록 (2026-09-24 UTC · 분석=Opus 5.5)

- 변경: `infra/demo/aws/site/index.html` — `.sgroup` 뒤에 `@media (min-width: 860px)` 블록 1개(열 머리 배치 +
  카드 2행 고정), 729 (d) 의 HTML 주석에 731 한 줄 추가. DOM·JS 무변경.
- 스크린샷: 가짜(stub) API 하네스(`TASK-MONO-729` 가 쓴 것, 실제 제어 API 호출 0)로 9개 장면 × 1280/400 —
  1280 은 세 카드 한 줄 + 열 머리, 400 은 세로 1열(변경 전과 동일).
- 🔵 전 장면의 1280 판이 한 줄이다 — 카드 높이가 상태마다 달라도 grid 가 행 높이를 맞춘다.
