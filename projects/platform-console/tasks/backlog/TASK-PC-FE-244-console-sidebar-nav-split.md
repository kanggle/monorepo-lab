# TASK-PC-FE-244 — `ConsoleSidebarNav.tsx` data/logic/render 분할

**Status:** backlog — candidate (2026-07-18 리팩토링 스윕 발굴)
**Area:** platform-console / console-web · `shared/ui/ConsoleSidebarNav.tsx` (546줄)
**Type:** `TASK-PC-FE` (frontend refactor, 순수 이동 — 행동 불변)
**Confidence:** MODERATE (경계는 깨끗하나 긴급도 낮음, 테스트성 이득이 주 동기)

## 발굴 근거

단일 `'use client'` 파일이 네 종류의 관심사를 혼재:

- **정적 nav-tree 데이터** — `GROUPS` const (L45–349, ~300줄): IAM/WMS/SCM/Finance/ERP/E-Commerce 그룹 taxonomy + 인라인 근거 주석.
- **순수 route-matching 헬퍼** — `matchesRoute`·`activeHref`·`parentKeyForPath` (L354–382): framework-agnostic, **독립 단위테스트 가능한 로직인데 현재는 컴포넌트를 마운트해야만 도달** 가능.
- **아이콘 컴포넌트** — `ChevronRight`·`ChevronLeft` (L393–431): 표현 leaf, nav 데이터·매칭과 무관.
- **stateful 컴포넌트** — L433–546: drill-in UI + `useEffect` route-sync.

## Proposed split (저장소 기존 패턴 = `*-guide/data.ts` + `Screen.tsx` 미러)

- `shared/ui/console-nav-config.ts` — `GROUPS`, `NavNode`/`NavLeaf`/`NavParent` 타입, `isParent`
- `shared/ui/console-nav-matching.ts` — `matchesRoute`·`activeHref`·`parentKeyForPath` (순수, 격리 단위테스트)
- `shared/ui/ConsoleSidebarNav.tsx` — 컴포넌트 + `leafClass` + 두 chevron 아이콘(또는 공용 아이콘 파일 존재 시 hoist)

경계가 깨끗한 근거: data/logic/render 사이를 넘는 공유 mutable state 없음. 매칭 함수는 프로젝트 테스트 컨벤션이 격리 단위테스트를 원하는 바로 그 종류의 로직.

## backlog → ready 게이트

- [ ] 분할 후 call-site import 무변경(배럴 또는 명시 경로) 확인.
- [ ] AC: route-matching 헬퍼 단위테스트 신설(분할의 주 이득), vitest 회귀 0, `pnpm lint`/`tsc` clean. 행동 불변.

## Reference

- 발굴: 2026-07-18 콘솔 리팩토링 발굴 스윕(god-file 스캔). 동 스윕에서 `shared/api/errors.ts`(1009줄)는 응집 에러 카탈로그로 **분할 제외** 판정.
- 패턴 선례: TASK-PC-FE-233 (ledger types god-file 분할).
