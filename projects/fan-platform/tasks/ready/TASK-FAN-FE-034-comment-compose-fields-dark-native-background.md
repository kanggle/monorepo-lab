# Task ID

TASK-FAN-FE-034

# Title

댓글 입력란 · 글쓰기 제목 · 본문 칸이 OS 다크 모드에서 어두운 바탕에 검은 글씨 — `TASK-FAN-FE-026` 의 공용 클래스(`FORM_FIELD_CLASS`)로 옮겨지지 않은 형제 셋

# Status

ready

# Owner

fan-platform

# Task Tags

- frontend
- community

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (className 세 곳 교체 + 배선 시험)
> Drafted 2026-10-28 UTC. 출처: 36차 데모 창 중 소유자 관찰 — «팬플랫폼에서 댓글 남기는 영역이 어두운색에 글씨도 검은 글씨라 잘 안 보인다, 밝은 바탕으로». 사실은 origin/main `2bd2f669e` 에서 읽었다.

---

# 배경

## 현재 코드 상태 (2026-10-28 UTC 실측)

- `projects/fan-platform/web/fan-platform-web/src/features/post/ui/CommentPanel.tsx:105` — 댓글 입력 `<textarea>` 의 className 이 `rounded-md border border-ink-200 px-3 py-2 text-sm text-ink-900 dark:bg-ink-800 dark:text-ink-100 dark:border-ink-700` 이다. **바탕색이 없고** 글자는 `ink-900`(검정)으로 고정.
- `src/app/globals.css:5-7` `:root { color-scheme: light dark; }` — OS 가 다크면 브라우저가 바탕을 안 적은 입력란에 **다크 네이티브 바탕**을 칠한다 ⇒ 어두운 바탕 + 검은 글자.
- `tailwind.config.ts:13` `darkMode: 'class'` 이고 `<html>` 에 `dark` 클래스를 다는 곳이 없다 ⇒ 위의 `dark:` 변형은 **한 번도 발화하지 않는다.**
- 이 결함은 이미 한 번 진단·수정됐다: `src/shared/ui/formField.ts:1-20` `FORM_FIELD_CLASS`(`TASK-FAN-FE-026`, PR #4064) — 바탕(`bg-white`) · 글자(`text-ink-900`) · `[color-scheme:light]` 셋을 모두 고정한 공용 클래스. 그 수정이 아티스트 검색칸 · 피드 필터 · 결제 기간 선택칸에만 적용됐고, **댓글 입력란과 글쓰기 폼 두 칸은 옛 문자열을 그대로 들고 있다**:
  - `src/features/post/ui/ComposeForm.tsx:54` 제목 `<input>` — 같은 문자열.
  - `src/features/post/ui/ComposeForm.tsx:67` 본문 `<textarea>` — 같은 문자열.
- 댓글 **목록**은 이 결함이 없다: 댓글 섹션이 `memberPostDetail.tsx:91-93` 의 `bg-white` `<article>` 안(`:140`)에 렌더된다. 문제는 바탕을 스스로 칠하는 네이티브 입력 부품뿐이다.

---

# Goal

OS 가 다크 모드여도 댓글 입력란과 글쓰기 폼의 제목 · 본문 칸이 흰 바탕 + 검은 글자로 보인다. 같은 사실(입력칸 색)은 `FORM_FIELD_CLASS` 한 집에만 둔다.

# Scope

## In Scope

1. `CommentPanel.tsx` 댓글 `<textarea>` · `ComposeForm.tsx` 제목 `<input>` · 본문 `<textarea>` 의 className 을 `FORM_FIELD_CLASS` 로 바꾼다(필요하면 레이아웃 클래스만 덧붙인다 — 색 클래스는 덧붙이지 않는다). 발화하지 않는 `dark:` 변형은 지운다.
2. 배선 시험 — 세 칸이 `FORM_FIELD_CLASS` 를 쓰는지(`bg-white` · `[color-scheme:light]` 포함) 단언.

## Out of Scope

- 사이트 전체 다크 모드 지원(`darkMode` 전략 · `globals.css` 의 `color-scheme`) — `TASK-FAN-FE-026` 과 같은 판단(입력칸은 밝게 고정)을 따른다.
- `text-ink-500`(토큰에 없는 색, `CommentPanel.tsx:108` · `:124`) — 별개 문제, 관찰만 기록.

# Acceptance Criteria

- [ ] **AC-0** — 위 «현재 코드 상태» 의 줄(`CommentPanel.tsx:105` · `ComposeForm.tsx:54,67` · `formField.ts:19-20` · `globals.css:5-7` · `tailwind.config.ts:13`)을 착수 시점에 다시 읽고, `src/` 의 다른 `<input>`/`<textarea>`/`<select>` 중 `FORM_FIELD_CLASS` 를 안 쓰고 바탕색도 없는 칸이 더 있는지 전수 확인해 기록한다(있으면 같은 PR 에 포함).
- [ ] **AC-1 (배선, 수정 전 빨강)** — 단위 시험: 렌더한 `CommentPanel`(로그인 뷰어) 의 `comment-input` 과 `ComposeForm` 의 제목 · 본문 칸 className 에 `bg-white` 와 `[color-scheme:light]` 가 있다. 수정 전 코드에서 빨강임을 확인해 구현 보고에 적는다.
- [ ] **AC-2 (계산값)** — 로컬 브라우저(Playwright `colorScheme: 'dark'`)에서 세 칸 중 하나 이상의 계산된 `background-color` 가 흰색 · `color` 가 `ink-900` 이다. 로그인이 필요한 화면이라 렌더할 수 없으면 ⚪ + 사유와 함께 AC-1 배선으로 갈음한다(`TASK-FAN-FE-026` AC-2 와 같은 처리).
- [ ] **AC-3 (회귀)** — `tsc --noEmit` · `next lint` · vitest 전체 · `next build` rc=0 (각각 따로 실행, rc 직접 확인).
- [ ] **AC-4 (라이브)** — Vercel Production 배포 후 OS 다크 모드 브라우저에서 팬 상세 화면 댓글 입력란이 흰 바탕 · 검은 글씨로 보인다(소유자). 못 재면 ⚪ + 사유.

# Related Specs

- `projects/fan-platform/specs/services/fan-platform-web/overview.md`
- `projects/fan-platform/specs/services/fan-platform-web/architecture.md` § Package Layout

# Related Contracts

- 없음 (화면 스타일만).

# Edge Cases

- 포커스 링 — `FORM_FIELD_CLASS` 가 `focus:border-brand-500 focus:ring-2` 를 함께 준다. 기존 두 칸에는 포커스 스타일이 없었다 — 바뀌는 것은 포커스 시 보라 테두리가 생기는 것뿐이고, 의도된 통일이다.
- OS 라이트 모드 — 바탕이 원래도 흰색이라 눈에 보이는 변화는 포커스 링뿐이다.
- placeholder — `placeholder:text-ink-400` 이 함께 들어와 «댓글을 남겨보세요» 가 회색으로 보인다(이전에는 브라우저 기본값).

# Failure Scenarios

1. 바탕만 `bg-white` 로 덧붙이고 `[color-scheme:light]` 를 빼면 — 네이티브 부품(스크롤바 등)은 여전히 다크로 그려진다(`formField.ts:14-15`).
2. 공용 상수 대신 같은 문자열을 복사하면 — 다음 수정이 한 집에만 들어간다(이번 결함이 바로 그 모양이다).
3. `dark:` 변형을 남겨 두면 — 나중에 누가 `<html class="dark">` 를 달 때 입력칸만 어두워져 `FORM_FIELD_CLASS` 칸들과 갈라진다.
