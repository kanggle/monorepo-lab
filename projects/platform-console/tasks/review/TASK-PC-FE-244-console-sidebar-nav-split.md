# TASK-PC-FE-244 — `ConsoleSidebarNav.tsx` data/logic/render 분할

**Status:** review
**Area:** platform-console / console-web · `shared/ui/ConsoleSidebarNav.tsx` (546→194줄)
**Type:** `TASK-PC-FE` (frontend refactor — 순수 이동, 행동 불변)
**Lifecycle:** backlog(2026-07-18 발굴) → ready(가드 이슈 해소 승격) → 구현 → review

> **구현 완료 (2026-07-18, 미머지)**: `console-nav-config.ts`(334, GROUPS+타입+isParent) · `console-nav-matching.ts`(40, 순수 헬퍼) 분리, `ConsoleSidebarNav.tsx` 546→194(컴포넌트·DOM·클래스·testid 불변). `domain-health-nav.test.tsx` 가드는 NAV_PATH만 `console-nav-config.ts` 로 재조준(단언 무변경). 신설 `console-nav-matching.test.ts`(15 케이스). 검증: tsc 0 · lint clean · 매칭 15/15 + nav 소비자 13파일/100 GREEN(전체 스위트 0 failures; count 미세 감소는 junction worker flake).

---

# Goal

단일 `'use client'` 파일 `shared/ui/ConsoleSidebarNav.tsx` 가 혼재한 네 관심사를 분리 — 정적 nav 데이터 / 순수 route-matching 로직 / 아이콘 / stateful 컴포넌트. **행동·DOM·클래스 불변**, route-matching 헬퍼는 격리 단위테스트 가능해진다(분할의 이득).

# Scope

## In

- **신규** `shared/ui/console-nav-config.ts` — `GROUPS` const + nav 타입(`NavNode`/`NavLeaf`/`NavParent` 등) + `isParent`.
- **신규** `shared/ui/console-nav-matching.ts` — 순수 헬퍼 `matchesRoute`·`activeHref`·`parentKeyForPath`(React import 없음).
- `shared/ui/ConsoleSidebarNav.tsx` — 컴포넌트 + `leafClass` + chevron 아이콘 2개. 두 신규 모듈에서 import. 컴포넌트 export·호출부(`(console)/layout.tsx` + 20개 테스트가 컴포넌트만 import) 무변경.
- **신규** `tests/unit/console-nav-matching.test.ts` — 헬퍼 격리 단위테스트(exact-match·nested prefix·active-parent 해석·root `/` edge). **현행 동작 기준**(신규 시맨틱 발명 금지).

## 🔴 필수 — source-string 가드 재조준 (HARDSTOP-06 해소)

`tests/unit/domain-health-nav.test.tsx` 는 **`ConsoleSidebarNav.tsx` 를 `readFileSync` 로 읽어 리터럴 문자열**(`'nav-dashboards'`·`'/dashboards/overview'`·`'nav-erp'`·`label:'ERP'`·`nav-finance` vs `nav-erp` 순서·`nav-domain-health`/`/dashboards/health` 부재 등)을 단언하는 whitebox 가드다. `GROUPS` 를 `console-nav-config.ts` 로 옮기면 그 리터럴이 원본에서 사라져 **이 기존 테스트가 깨진다.**

→ **이 가드의 대상 파일 상수(`NAV_PATH` 등)를 `console-nav-config.ts` 로 재조준**한다(리터럴이 이동한 곳). **이것은 허용된 수정** — 가드의 목적(nav taxonomy 리터럴 pin)은 보존되고 파일 레이아웃만 따라간다. 행동 변경 아님. **이 한 파일만 수정 허용**, 그 외 기존 `*.test.*` 무수정.

## Out

- nav taxonomy·라우팅·DOM·클래스·컴포넌트 동작 일체 변경 금지(순수 구조 이동).

# Acceptance Criteria

- [x] 4관심사 3파일 분리(config/matching/component), 컴포넌트 호출부 import 무변경.
- [x] `console-nav-matching.test.ts` 신설(헬퍼 격리 커버).
- [x] `domain-health-nav.test.tsx` 가드를 `console-nav-config.ts` 로 재조준(그 외 테스트 무수정).
- [x] 검증: `tsc --noEmit` 0 · `next lint` clean · `vitest run` GREEN.

# Related Specs

- 정경 없음(구조 리팩토링). 패턴 선례: TASK-PC-FE-233(ledger types 분할)·PC-FE-235(hook-split).

# Edge Cases / Failure Scenarios

- `domain-health-nav.test.tsx` 를 놓치면 vitest RED — 반드시 NAV_PATH 재조준.
- 배럴 re-export 로 `readFileSync` 가드를 만족시킬 수 없다(가드는 import 문이 아니라 resolved 리터럴을 봄) → 대상 경로 자체를 config 파일로 바꿔야 함.
- 헬퍼를 컴포넌트 밖으로 뺄 때 활성-부모 해석이 `usePathname` 결과에 의존하지 않는 순수 함수인지 확인(순수여야 격리 테스트 성립).

# Review Notes

- 발굴: 2026-07-18 리팩토링 스윕. HARDSTOP-06(source-string 가드 충돌)은 착수 조사 중 발견 → 스펙에 가드 재조준을 명문화해 해소.
