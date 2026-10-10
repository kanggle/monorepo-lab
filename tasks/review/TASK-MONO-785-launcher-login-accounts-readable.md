# Task ID

TASK-MONO-785

# Status

review — 구현 완료(2026-10-10 UTC). 론처 마크업 · CSS 만 바뀌고 계정 값은 그대로다.

# Title

론처 「로그인 계정」 영역을 계정별 카드로 다시 정리한다 — 어떤 계정을 언제 쓰는지 한눈에 읽히게

# Owner

monorepo

# Task Tags

- demo
- launcher

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 — 마크업 재배치 + CSS. 계정 · 가드 · 시드 무변경.

---

# Dependency Markers

- 관련: `TASK-MONO-782`(재굽기 뒤 CS 계정 추가) — 같은 블록을 고친다. 이 티켓 뒤에는 그 계정이 **네 번째 카드**(`<section class="acct">`)로 들어가면 된다.
- 선례: `TASK-MONO-561`(블록 신설) · `TASK-MONO-730`(viewer) · `TASK-MONO-751`(platform).

# Goal

소유자 요청(2026-10-10): 「런처에서 로그인 계정 부분 가독성 있게 정리」.

기존 블록은 계정 세 개(`demo` · `viewer` · `platform`)가 **문단 + 자격 두 줄** 의 반복으로 이어져, 어떤 계정이 무엇을 위한 것인지와 로그인 후 무엇을 해야 하는지(테넌트 선택)가 긴 산문 속에 묻혀 있었다.
계정 하나 = 카드 하나로 나누고, 각 카드를 **이름 · 용도 한 줄 · 자격 · 로그인 후 단계(번호) · 보충 설명(작은 글씨)** 의 같은 모양으로 맞춘다.

# Scope

## In

- `infra/demo/aws/site/index.html` 「로그인 계정」 카드의 마크업 · 해당 CSS.
- 카드 순서: 기본 데모 계정(추천) → 플랫폼 운영자 → 권한 없음 시연(가장 덜 쓰이는 것을 끝으로).
- 넓은 화면은 3열 그리드, 좁은 화면(≈420px)은 세로로 쌓인다(`repeat(auto-fit, minmax(290px, 1fr))`).

## Out

- 계정 값 · 시드 · (z11) 가드 변경 — 없음.
- CS 계정 추가(`TASK-MONO-782`).

# Acceptance Criteria

- [x] **AC-1** — 세 계정이 각자 카드로 분리되고, 각 카드가 용도 한 줄 · 이메일/비밀번호(복사 버튼) · 로그인 후 단계를 가진다. 기존 문구의 사실(demo-corp 선택 → 5개 도메인 권한 · 이커머스 목록은 `ecommerce` 테넌트 · JWT `tenant_id` 이유 · 2026-09-11 실측 · fan-platform → 「팬 디렉터리」 · viewer 의 대부분 메뉴 403 · `/partnerships` 예외)이 하나도 빠지지 않는다.
- [x] **AC-2** — (z11) 의 추출식이 네 값을 그대로 뽑는다. 실측(가드와 같은 `sed` 식): `c-email=demo@demo.com` · `c-pass=Demo1234!` · `c-viewer-email=viewer@demo.com` · `c-platform-email=platform@demo.com`. 각 `<code id=…>값</code>` 은 한 줄을 유지한다.
- [x] **AC-3** — 렌더 확인: Playwright 로 폭 1200px(3열) · 420px(세로 쌓임) 스크린샷을 찍어 눈으로 확인 — 가로 넘침 없음.
- [x] **AC-4** — 유지보수 주석(`TASK-MONO-561` · `730` · `751` 의 «값을 손으로 고치지 마라» · 권한 파생 근거)은 해당 카드 옆으로 옮겨 그대로 둔다.

# Related Specs

- `infra/demo/aws/README.md` § 배포 층 (론처는 머지 즉시 Vercel 로 나간다 — 이 변경은 이미 로그인되는 계정만 다루므로 즉시 공개가 안전하다).

# Related Contracts

- 없음.

# Edge Cases

| 상황 | 기대 |
|---|---|
| 기본 계정 카드가 다른 둘보다 길다 | `align-items: start` 로 짧은 카드가 늘어나지 않는다 |
| 좁은 화면 | 카드가 세로로 쌓이고 본문 가로 스크롤 없음 |
| `TASK-MONO-782` 가 CS 계정을 추가 | 같은 모양의 `<section class="acct">` 하나 추가 — 그리드가 자동으로 받는다 |

# Failure Scenarios

1. 정리하면서 `<code id="c-…">` 를 여러 줄로 쪼갠다 → (z11) 의 줄 단위 `sed` 가 값을 못 뽑아 빨개진다(AC-2 가 막는다).
2. 「왜」 문장을 지운다 → 이커머스 빈 목록을 결함으로 오진하는 길이 다시 열린다(`TASK-MONO-554`). 작은 글씨로 남긴다.
