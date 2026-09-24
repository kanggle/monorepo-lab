# Task ID

TASK-MONO-732

# Status

done (2026-09-24 UTC)

# Title

론처 카드를 세로로 쌓고 카드 **안**을 가로로(왼쪽 스크린샷 · 오른쪽 설명) — 소유자 스케치대로. `TASK-MONO-731` 의 «한 줄 3열» 을 되돌린다

# Owner

monorepo

# Task Tags

- demo
- launcher

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 — CSS 미디어쿼리 한 블록.

---

# Goal

소유자가 스케치로 원하는 모양을 보였다(2026-09-24): **가로로 긴 카드가 세로로 쌓이고, 각 카드는 왼쪽 좁은 칸 +
오른쪽 넓은 칸으로 나뉜다.** 직전 요청 «세로로 나열 → 가로로 나열» 을 `TASK-MONO-731`(#4003)은 «세 카드를 한 줄에» 로
읽었는데, 원한 것은 «카드 **안**의 가로» 였다 ⇒ 731 의 배치를 되돌리고 스케치대로 바꾼다.

# Scope

## In
- `#surfaces` 를 모든 폭에서 1열(634 의 데스크톱 3열 규칙 제거).
- 860px 이상: `.scard` 를 가로(flex row) — `.shots` 34% 왼쪽, `.body` 오른쪽. `dl` 을 «라벨 | 값» 2열로.
  스크린샷은 `object-position: left top`(좁은 칸에서 화면 왼쪽이 잘리지 않게).
- 731 의 열 머리 규칙 제거 — 구획 제목(「서비스 화면」·「운영 도구」)은 다시 줄 전체(729 (d) 원래 모양).

## Out
- 모바일(<860px): 기존 그대로(스크린샷 위 · 설명 아래).
- DOM 순서 · 판정 속성 · 문구 · JS — 변경 없음.

# Acceptance Criteria

- [x] **AC-1** — 1280px: 카드 세로 3개, 각 카드 왼쪽 스크린샷 · 오른쪽 설명(스크린샷).
- [x] **AC-2** — 400px: 기존과 같음(스크린샷).
- [x] **AC-3** — `bash infra/demo/verify-demo-wrapper.sh` 정적 rc=0.
- [x] **AC-4** — 필수 가드 3종 rc=0(스테이지 후).

# Related Specs

- `tasks/review/TASK-MONO-731-launcher-cards-in-one-row.md` (되돌리는 대상)
- `tasks/review/TASK-MONO-729-launcher-status-and-visitor-stop-control.md` § (d)

# Related Contracts

- 없음.

# Edge Cases

| 상황 | 기대 |
|---|---|
| 스크린샷 0장(캐러셀 자리 표시) | `.shots` 가 `min-height: 240px` 로 자리를 지킨다 |
| 설명이 스크린샷보다 짧음 | flex 행 높이가 `min-height` 로 하한 |

# Failure Scenarios

1. 요청 문장만 보고 다시 추측 → 이번엔 스케치가 권위다. 모양은 스케치와 대조해 스크린샷으로 확인했다.

---

# 구현 기록 (2026-09-24 UTC · 분석=Opus 5.5)

- 변경: `infra/demo/aws/site/index.html` CSS 만 — 634 의 `repeat(3, 1fr)` 제거, 731 블록을 732 블록으로 교체, 관련 주석 두 곳 갱신.
- 스크린샷: 가짜(stub) API 하네스(실제 제어 API 호출 0) 9장면 × 1280/400.
- 🔴 **내 해석 오류 기록**: 731 은 «가로로 나열» 을 카드 배열로 읽었다. 모양 요청은 말보다 그림이 싸다 — 모호하면 스케치를 청한다.
