# Task ID

TASK-PC-FE-297

# Title

콘솔 내비게이션 — 2뎁스 순서(가이드 우선) · 사이드바 아이콘 · 파비콘/앱 아이콘 신설

# Status

review

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

---

# Implementation Record (2026-09-24 UTC)

> 구현=Opus 5.5(서브에이전트). worktree `ml-wt-b-297`, 브랜치 `feat/pc-fe-297-nav-guide`. 298 과 같은 PR(별도 커밋).

## 변경 파일

- `src/shared/ui/console-nav-config.ts` — 6개 도메인 children 순서를 가이드 → 개요로 뒤집음. 옛 순서를 정당화하던 인접 주석 6곳(IAM · WMS · SCM · Finance · ERP · E-Commerce)을 **같은 커밋에서** 가이드-먼저 근거(2026-09-24 소유자 결정, ADR-MONO-074 샘플 방문자가 먼저 읽는 화면)로 다시 씀. testid·href 무변경. `NavIconName` 타입 + 모든 노드에 **필수** `icon` 필드(타입 검사기가 아이콘 없는 항목 추가를 막는다).
- `src/shared/ui/console-nav-icons.tsx` (신규) — 손으로 쓴 인라인 SVG 36종, `aria-hidden`. `package.json` 에 아이콘 라이브러리 **없음을 확인**하고 의존성 추가 0.
- `src/shared/ui/ConsoleSidebarNav.tsx` — 아이콘 렌더 · 그룹 제목 하위화 · 접힌 드릴의 부모 활성 표시 · `navPathFor` 별칭 적용.
- `src/shared/ui/console-nav-matching.ts` — `NAV_ROUTE_ALIASES` + `navPathFor()`.
- `src/app/icon.svg` · `src/app/apple-icon.tsx` · `src/app/manifest.ts` (신규) — Next.js 메타데이터 파일 컨벤션.
- 테스트: `tests/unit/sidebar-nav-order-icons.test.tsx` (신규 13케이스), `sidebar-drilldown.test.tsx`(Finance·ERP 순서 단언 2건 **의도적으로** 갱신), `console-nav-matching.test.ts`(타입 필드), 주석만: `wms-guide-nav` · `scm-guide-nav` · `ecommerce-guide-nav` · `inbound-nav` · `operations-nav` · `master-nav` · `sidebar-iam-group` 테스트, `finance-guide/data.ts` · `erp-guide/data.ts` 의 화면 순서 주석.

## AC 별 판정

- **AC-1** ✅ — 6개 드릴 전부 `children[0]=가이드, children[1]=개요` (신규 테스트가 config 와 렌더 DOM 양쪽에서 단언). 인접 주석 갱신.
- **AC-2** ✅ — 모든 항목(최상위 링크·드릴 부모·드릴 자식)에 아이콘 1개(`svg[data-nav-icon]`, 테스트가 개수=1 단언). 그룹 제목: `text-xs font-medium uppercase tracking-wider` → `text-[11px] font-normal text-muted-foreground/70` — 요소는 그대로 `<p>`(heading 이 아니었고 지금도 아님, 접근성 트리 무변). 브라우저 스크린샷 확인.
- **AC-3** ✅ — **재현 스텝**: `/wms/outbound` 딥링크 → 드릴 자동 열림 → 고정된 WMS(부모) 클릭으로 접기 → 이전엔 최상위 목록의 WMS 버튼에 아무 표시도 없었다(`ConsoleSidebarNav.tsx` 옛 :158-175 는 부모 버튼에 무조건 `text-muted-foreground` 만 줬다). **after DOM**(프로덕션 빌드, 브라우저 실측): `aria-current="true"` · `data-active="true"` · `bg-accent font-medium text-foreground`. `aria-current="page"` 가 아니라 `"true"` 인 이유: 버튼은 페이지가 아니다 — «현재 항목이 이 안에 있다» 를 뜻하는 값. 다른 부모에는 붙지 않음(테스트 단언). before 스크린샷은 찍지 않았다 — before 상태는 옛 코드 줄로 인용했다.
- **AC-4** ✅ — `/dashboards/health` 는 `navPathFor` 별칭으로 **개요**(`/dashboards/overview`)에 매칭(브라우저 실측 `nav-dashboards[aria-current=page]`). 항목을 추가하지 않고 매칭 규칙을 조정한 이유: `TASK-PC-FE-068` 이 «도메인 상태는 최상위 항목이 아니다» 로 결정했고 `domain-health-nav.test.tsx` 가 nav config 에 `'/dashboards/health'` 리터럴이 없음을 단언한다 — 별칭은 `console-nav-matching.ts` 에 두어 그 결정을 보존. **`/account` 는 의도적 미매칭** — 근거: 로그인한 운영자 **자신의** 계정 설정이며 상단 계정 메뉴(`AccountMenu.tsx` → `/account`)에서 들어오는 화면이다(`account/page.tsx` 헤더 «Reached from the top-bar account menu», AWS/GCP 도 계정 설정을 사이드바가 아닌 계정 메뉴에 둔다). 사이드바의 어느 섹션도 그 부모가 아니므로 무엇을 켜든 거짓 «현재 위치» 가 된다. 테스트가 `/account` 에서 `aria-current` 0개를 단언.
- **AC-5** ✅ — 프로덕션 빌드의 `<head>`: `link[rel=icon] /icon.svg (image/svg+xml)`, `link[rel=apple-touch-icon] /apple-icon (image/png, 200)`, `link[rel=manifest] /manifest.webmanifest` → `200 application/manifest+json`, JSON 파싱 성공(name/short_name/start_url/display/icons 2개). 흰 배경·어두운 배경(#202124) 양쪽에 64px·16px 로 렌더해 가독 확인 — 검은 타일은 밝은 탭에서, `#52525b` 테두리와 흰 글리프는 어두운 탭에서 윤곽을 만든다.
  - 🔵 **티켓 문구와의 차이**: 사이드바 아이콘은 「흰색」 리터럴이 아니라 `stroke="currentColor"` 다. 콘솔 테마 기본값은 `system`(`ThemeProvider.tsx`)이라 라이트 테마 사이드바는 흰 바탕이고, 리터럴 `#fff` 는 거기서 **안 보인다**. `currentColor` 는 다크 테마에서 흰색, 라이트 테마에서 글자색으로 그려진다(두 테마 스크린샷 확인). 파비콘/앱 아이콘은 티켓대로 검은 배경 + 흰 아이콘.
- **AC-6** ✅ — nav 관련 유닛 스위트 29파일/175케이스 초록(신규 포함). testid 무변경 — DOM 순서만 바뀜. `aria-current="page"` 규칙(최장 일치)·aria-label·키보드(네이티브 `<a>`/`<button>`) 회귀 없음. 순서를 핀하던 단언은 `sidebar-drilldown.test.tsx` 의 Finance·ERP 2건뿐이었고 둘 다 의도적으로 뒤집었다.
- **AC-7** ✅ — `tsc --noEmit` rc=0 · `next lint` rc=0 · `next build` rc=0(경고 1건은 기존 `@opentelemetry/winston-transport` — 무관) · 전체 vitest: 317파일 중 9파일/12케이스가 **5s 타임아웃**으로 빨갛게 나왔고(같은 호스트에서 다른 에이전트의 프런트 스위트가 병렬 실행 중), 그 9파일을 `--minWorkers=1 --maxWorkers=1` 로 직렬 재실행하자 85/85 초록 — 전부 타임아웃 지문이었고 단언 실패 0. 브라우저: 1280px 다크/라이트 + 400px 다크/라이트, 샘플 방문자(쿠키 없음)로 `/console` 200.
  - 400px 에서 사이드바는 **원래부터** `hidden md:block`(`(console)/layout.tsx`)이라 렌더되지 않는다 — 아이콘으로 인한 줄바꿈/겹침이 생길 자리가 없다(모바일 드로어는 기존 deferred). 데스크톱 폭에서는 라벨에 `truncate` 를 걸어 긴 라벨(`보충 계획 설정`)도 한 줄 유지.
  - 브라우저 확인 뒤 한 가지를 바꿨다: `dashboard`(개요) 글리프가 `catalog`(카탈로그)의 네 칸 격자와 거의 같아 보여 **게이지 글리프로 교체**. 그 뒤 판은 298 커밋의 최종 빌드·스크린샷이 확인한다.
- **AC-8** ✅(기록만) — `node scripts/check-capture-route-staleness.mjs` rc=0 (`console 라우트 67 probe /dashboards/overview ✔`). 저장소 README 들에 사이드바 스크린샷 캡션은 없다(`README.md` · 프로젝트/앱 README 이미지 링크 0건). 사이드바가 찍힌 촬영물은 론처 썸네일 `infra/demo/aws/site/thumbnails/console-1-erp-masters.jpg` · `console-2-ecommerce-products.jpg` 이고, 이 변경으로 **시각적으로 낡았다**(아이콘 없음, 옛 그룹 제목 스타일). 티켓 범위 밖이므로 재촬영하지 않고 여기 기록만 남긴다.
