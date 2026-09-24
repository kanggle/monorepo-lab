# Task ID

TASK-PC-FE-297

# Title

콘솔 내비게이션 — 2뎁스 순서(가이드 우선) · 사이드바 아이콘 · 파비콘/앱 아이콘 신설

# Status

ready

# Owner

frontend

# Task Tags

- code
- frontend
- console-web

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5.

---

# Dependency Markers

- **후속 (blocks)**: `TASK-PC-FE-298` 이 같은 파일(`console-nav-config.ts`)을 건드린다 — 이 티켓을 **먼저**, 같은 worktree에서 **직렬로** 완료한 뒤 298을 시작한다(병렬 worktree 금지 — 같은 파일 시리즈는 병렬화하면 병합 충돌이 보장된다, `CLAUDE.md` § Git 규율).

---

# Goal

콘솔 6개 도메인(IAM·WMS·SCM·Finance·ERP·E-Commerce)의 2뎁스 메뉴 순서를 「가이드 → 개요 → 기능들」로 바꾸고, 사이드바에 항목별 아이콘 + 그룹 제목의 시각적 종속 처리를 추가하고, 지금까지 없던 파비콘/앱 아이콘을 신설한다.

# Scope

## In Scope

- **2뎁스 순서 변경**: `projects/platform-console/apps/console-web/src/shared/ui/console-nav-config.ts` 안 6개 도메인 전부(2026-09-24 UTC 실측 확인 — IAM :70-71, WMS :147-150, SCM :196-197, Finance :239-240, ERP :258-259, E-Commerce :282- 부근, 각각 `개요` 다음에 `가이드`)를 **가이드 → 개요** 순서로 바꾼다.
  - 🔴 **주의** — 이 파일의 인접 주석들(예: IAM :58-62 "개요(TASK-PC-FE-180)가 첫 자식인 이유는 그것이 LIVE operator 착지점이기 때문", WMS :148-150 "IAM 의 개요→가이드 순서와 동일하게", SCM :178-180, ERP :250-252, E-Commerce :283-285)은 **지금의 개요-먼저 순서가 의도적 설계였다**고 명시적으로 설명한다. 이 티켓은 소유자가 2026-09-24 명시적으로 다시 결정한 것이므로 순서 자체는 그대로 뒤집되, **이 설명 주석들도 같은 커밋에서 가이드-먼저로 갱신**한다 — 순서만 바꾸고 주석이 옛 순서를 정당화한 채로 남으면 다음 사람이 다시 되돌리는 왕복이 생긴다.
- **사이드바 아이콘**: `ConsoleSidebarNav.tsx`(:41-79, :147-191)는 현재 텍스트 + 화살표(chevron) SVG만 렌더한다(2026-09-24 UTC 실측 확인). 항목별로 흰색 인라인 SVG 아이콘을 추가한다 — 새 아이콘 라이브러리 의존성을 추가하지 않는다(먼저 `package.json` 에 이미 아이콘 라이브러리가 있는지 확인하고, 없으면 인라인 SVG로 직접 작성).
- **그룹 제목 종속 처리**: 그룹 헤딩(관리/고객 신원/조직 설정/도메인 운영)을 항목보다 시각적으로 하위로 보이게 한다(글자 크기 축소, 톤 다운, 여백 조정) — DOM 구조/접근성 트리는 그대로 유지.
- **활성 상태 공백 수리**: 드릴다운을 수동으로 접었을 때 상위 목록의 부모 버튼에 활성 표시가 없는 문제(`ConsoleSidebarNav.tsx:158-175`)를 고친다.
- **매칭 안 되는 라우트 처리**: `/dashboards/health` 와 `/account` 가 어떤 nav 항목과도 매칭되지 않는다 — `/dashboards/health` 는 항목을 추가하거나 매칭 규칙을 조정해서 고치고, `/account` 는 항목을 추가할지 매칭 없이 둘지 **결정하고 그 근거를 티켓 구현 기록에 남긴다**(둘 다 정답이 될 수 있음 — silent 미결정만 금지).
- **파비콘/앱 아이콘 신설**: `public/` 디렉터리에 `.gitkeep` 외 아무것도 없고 `src/app/layout.tsx:10-14` 메타데이터에 title/description만 있다(2026-09-24 UTC 실측 확인) — Next.js 메타데이터 컨벤션(`src/app/icon.svg`, `apple-icon`, `manifest`)으로 검은 배경 + 흰색 콘솔 아이콘을 추가한다. 동일 디자인으로 밝은 배경 위 가독성도 확인한다.

## Out of Scope

- 전역 가이드 메뉴 신설 + 권한/기능 매핑 문서 — `TASK-PC-FE-298`.
- 권한에 따른 메뉴 숨김/노출 로직 — 이 티켓은 순서·시각 디자인·아이콘만 다룬다.
- `scripts/check-capture-route-staleness.mjs` 가 지키는 스크린샷 재촬영 — 사이드바 스크린샷이 이 변경으로 시각적으로 낡아지지만, 재촬영은 이 티켓의 범위가 아니다(아래 AC에서 확인만 한다).

# Acceptance Criteria

- [ ] **AC-1** — 6개 도메인 전부 2뎁스 순서가 가이드 → 개요이고, 인접 설명 주석도 그 순서를 정당화하도록 갱신됐다.
- [ ] **AC-2** — 사이드바 각 항목에 아이콘이 있고, 그룹 헤딩이 시각적으로 항목보다 하위로 보인다(스크린샷 확인).
- [ ] **AC-3** — 드릴다운을 접은 뒤에도 상위 버튼에 활성 표시가 있다(재현 스텝 + before/after 스크린샷 또는 DOM 스냅샷).
- [ ] **AC-4** — `/dashboards/health` 가 nav 항목과 매칭된다(또는 매칭 규칙이 조정됐다). `/account` 처리(추가 or 의도적 미매칭)가 구현 기록에 근거와 함께 남아 있다.
- [ ] **AC-5** — 파비콘/앱 아이콘이 브라우저 탭에 보이고, 매니페스트가 유효하며, 밝은/어두운 배경 양쪽에서 가독된다.
- [ ] **AC-6** — 기존 26개 nav 관련 유닛 테스트(`tests/unit/*-nav.test.tsx`, `sidebar-drilldown.test.tsx`, `console-nav-matching.test.ts`)가 새 순서/구조에 맞게 갱신되어 통과한다. `aria-current`/aria-label/키보드 내비게이션이 회귀하지 않는다.
- [ ] **AC-7** — typecheck + build + 유닛 테스트가 로컬에서 통과한다. 데스크톱 폭 + ~400px 모바일 폭 양쪽에서 브라우저로 확인한다.
- [ ] **AC-8** — `scripts/check-capture-route-staleness.mjs` 를 돌려 결과를 기록하고, README 스크린샷 캡션 중 사이드바를 보여주는 것이 시각적으로 낡아졌다면 **주석으로만 남기고 재촬영은 하지 않는다**(범위 밖 명시).

# Related Specs

- `projects/platform-console/apps/console-web/src/shared/ui/console-nav-config.ts`
- `projects/platform-console/apps/console-web/src/shared/ui/ConsoleSidebarNav.tsx`
- `projects/platform-console/apps/console-web/src/shared/ui/console-nav-matching.ts`
- `projects/platform-console/apps/console-web/src/app/layout.tsx`
- 2026-09-24 UTC 포트폴리오 UX 전수조사(소유자 승인)

# Related Skills

- `.claude/skills/frontend/` — INDEX 참조.

---

# Related Contracts

- 없음(순수 프런트엔드 UI/네비게이션 변경, API 계약 무변경).

---

# Target App

- `apps/console-web`

---

# Edge Cases

- 그룹 헤딩을 시각적으로만 낮추는 과정에서 접근성 트리(heading level, landmark)가 깨지지 않아야 한다.
- 모바일 폭(~400px)에서 아이콘 추가로 항목 줄바꿈/겹침이 생기지 않아야 한다.
- 가이드-먼저 순서가 `testid`(`nav-iam-guide`, `nav-iam-overview` 등)를 바꾸지 않는지 — testid는 무변경, DOM 순서만 바뀐다는 것을 확인한다.

# Failure Scenarios

- 순서만 바꾸고 설명 주석을 갱신하지 않으면 다음 세션이 주석을 근거로 다시 되돌린다(왕복 재발) — AC-1이 이를 막는다.
- `testid`를 실수로 바꾸면 26개 nav 테스트가 전부 깨진다.
- 새 아이콘 라이브러리를 무심코 추가하면 번들 크기 증가 + `package.json` 리뷰 부담 — 반드시 먼저 기존 의존성을 확인한다.
