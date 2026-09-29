# Task ID

TASK-FAN-FE-026

# Title

결제 기간 선택칸·아티스트 검색칸이 OS 다크 모드에서 어두운 바탕에 검은 글자 — 흰 바탕으로 통일

# Status

review

# Owner

frontend

# Task Tags

- code
- frontend

---

# Dependency Markers

- 없음. `TASK-FAN-FE-025`(공개 피드 필터)가 만든 `PublicFeedFilter.tsx` 의 `FIELD` 를 같은 상수로 바꾼다 — 025 는 이미 `done/`.

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 (단순 스타일 fix). 실제 구현=Opus 5.5.

---

# Goal

소유자 보고(2026-09-29): 멤버십 가입의 **결제 기간** 선택창이 어두운 배경에 검은 글씨라 안 보인다 — 흰 배경으로. **아티스트 페이지 검색창**도 같은 스타일로 통일.

원인: `globals.css` 의 `:root { color-scheme: light dark }` 때문에 OS 가 다크면 바탕색을 안 적은 네이티브 컨트롤을 브라우저가 어둡게(`rgb(59,59,59)`) 칠한다. 한편 `<body>` 는 `text-ink-900` 이고 preflight 가 `color: inherit` 를 주므로 글자는 검정(`rgb(24,24,27)`)이다. 두 칸 모두 바탕·글자색을 적지 않았다(결제 기간의 `border-ink-300` 은 토큰에 없는 색이라 무효).

# Scope

## In Scope

- 공용 클래스 `shared/ui/formField.ts` 의 `FORM_FIELD_CLASS` 신설 — `bg-white` · `text-ink-900` · `placeholder:text-ink-400` · `[color-scheme:light]` · 기존 테두리/포커스 링.
- 적용: `SubscribePanel.tsx`(결제 기간 `<select>`), `app/(main)/artists/page.tsx`(검색 `<input>`), `PublicFeedFilter.tsx`(같은 결함 부류 — `dark:bg-ink-900` 는 `darkMode: 'class'` 인데 `dark` 클래스를 다는 곳이 없어 한 번도 발화하지 않는 죽은 변형이었다).

## Out of Scope

- 사이트 전체 다크 테마 도입/정리, 다른 컴포넌트의 존재하지 않는 `ink-300/500/700` 토큰 정리.

# Acceptance Criteria

- [x] **AC-1** — OS 다크(`colorScheme: 'dark'`)에서 아티스트 검색칸·피드 필터 칸의 계산된 바탕이 흰색, 글자가 `ink-900`.
- [x] **AC-2** — 결제 기간 선택칸이 같은 상수를 쓴다(로그인 뒤 화면이라 계산값 대신 배선으로 확인).
- [x] **AC-3** — tsc · lint · unit test · build rc=0.

# Related Specs

- `projects/fan-platform/web/fan-platform-web/src/app/globals.css`, `tailwind.config.ts`

# Related Contracts

- 없음(스타일 전용).

# Target App

- `web/fan-platform-web`

# Edge Cases

- 선택칸의 펼침 목록·검색칸의 지우기(×) 같은 네이티브 부품은 바탕색만으로는 밝아지지 않는다 → `[color-scheme:light]`.

# Failure Scenarios

- 바탕만 흰색으로 하고 글자색을 안 적으면, 상속 글자색이 바뀌는 순간(다크 테마 도입) 흰 바탕에 흰 글자가 된다 → 셋 다 고정.

---

# 구현 기록 (2026-09-29 UTC · 분석=Opus 5.5 · 구현=Opus 5.5)

## 대조군 — 결함 재현 (Playwright, `next start` 빌드, `colorScheme` 별)

같은 페이지에 **옛 클래스**를 그대로 단 요소를 주입해 계산값을 쟀다.

| scheme | 옛 결제 기간 select | 옛 아티스트 검색 input |
|---|---|---|
| dark | `rgb(59,59,59)` / `rgb(24,24,27)` | `rgb(59,59,59)` / `rgb(24,24,27)` |
| light | `rgb(255,255,255)` / `rgb(24,24,27)` | `rgb(255,255,255)` / `rgb(24,24,27)` |

⇒ 보고된 증상(어두운 바탕 + 검은 글자)이 OS 다크에서만 난다.

## AC-1 — ✅ 수정 후 (dark)

| 경로 | 칸 | 바탕 / 글자 / color-scheme |
|---|---|---|
| `/artists` | `input[name=q]` | `rgb(255,255,255)` / `rgb(24,24,27)` / `light` |
| `/` | `input[name=q]` | `rgb(255,255,255)` / `rgb(24,24,27)` / `light` |
| `/` | `select[name=artist]` | `rgb(255,255,255)` / `rgb(24,24,27)` / `light` |

빌드 CSS 에 `.\[color-scheme\:light\]{color-scheme:light}` 생성 확인(상수가 `.ts` 파일에 있어도 `content: ./src/**/*.{ts,tsx}` 가 잡는다).

## AC-2 — ✅ 배선

`SubscribePanel.tsx` 의 결제 기간 `<select className={FORM_FIELD_CLASS}>` — AC-1 에서 잰 칸과 같은 상수라 같은 계산값.

## AC-3 — ✅ 게이트 (개별 statement, rc 직접 확인)

| 게이트 | rc |
|---|---|
| `pnpm exec tsc --noEmit` | 0 |
| `pnpm run lint` | 0 |
| `pnpm run test` | 0 (37 files / 326 tests) |
| `pnpm run build` | 0 |

## 편차

- 결제 기간 칸의 세로 여백이 `py-1.5` → `py-2`(아티스트 검색칸과 통일).
