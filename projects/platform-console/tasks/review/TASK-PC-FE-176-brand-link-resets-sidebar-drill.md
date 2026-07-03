# TASK-PC-FE-176 — "Platform Console" 브랜드 클릭 시 사이드바 drill을 최상위 메뉴로 초기화

**Status:** review
**Area:** platform-console / console-web · **Route:** 전역 셸(모든 콘솔 라우트) · **Nav:** `ConsoleSidebarNav` drill 상태 · **Contract:** 변경 없음
**Analysis model:** Opus 4.8 · **Impl model:** Sonnet (단일 useEffect 가드 제거, 신규 아키텍처 결정 없음)

---

## Goal

상단 브랜드 링크 "Platform Console"(`layout.tsx` — `href="/dashboards/overview"`)을 누르면 개요로 이동하지만, 좌측 사이드바(`ConsoleSidebarNav`)가 이전 drill-in 상태(예: WMS ▸ 출고 하위 메뉴)를 **그대로 유지**한다. 개요로 왔는데 사이드바는 여전히 WMS 하위 메뉴에 머물러 최상위 목록(개요·카탈로그·도메인 목록)으로 돌아오지 않고, `개요` 항목이 강조되지도 않는다.

브랜드 클릭 시(그리고 어떤 drill 부모에도 속하지 않는 라우트로 이동할 때) 사이드바가 **최상위 메뉴로 초기화**되도록 한다.

## 배경 사실 (검증됨)

- `ConsoleSidebarNav`는 FE-059 이후 Vercel식 drill-in 사이드바: 부모(WMS/IAM/SCM/Finance/ERP/E-Commerce)를 열면 목록이 그 부모의 서브메뉴로 치환되고, `openKey` state가 현재 열린 부모를 보유한다.
- 라우트 동기화 `useEffect`가 `if (key) setOpenKey(key)` **가드**를 두어, `parentKeyForPath(pathname)`가 `null`인 라우트(개요 `/dashboards/overview`, 카탈로그 `/console` 등 부모에 속하지 않는 라우트)로 이동할 때 **openKey를 초기화하지 않는다.** → drill 상태가 잔류.
- 수동 펼침/접기(pinned 부모 클릭, 부모 토글 클릭)는 **라우트 변경 없이** state만 바꾸므로 이 effect는 발화하지 않는다 → 무조건 동기화로 바꿔도 수동 상호작용은 보존된다(effect deps=`[pathname]`).

## Scope

### 수정
1. **`src/shared/ui/ConsoleSidebarNav.tsx`** — 라우트 동기화 effect의 `if (key)` 가드 제거, 무조건 동기화로 변경:
   ```diff
   - const key = parentKeyForPath(pathname);
   - if (key) setOpenKey(key);
   + setOpenKey(parentKeyForPath(pathname));
   ```
   부모 라우트로 이동 → 해당 부모 열림(기존과 동일). 부모에 속하지 않는 라우트로 이동 → `null`로 최상위 목록 복귀(신규). 주석도 갱신.

### 테스트
2. **MODIFY `tests/unit/sidebar-drilldown.test.tsx`** — 2 케이스 추가:
   - drill된 자식 라우트(`/wms/outbound`)에서 비-부모 라우트(`/dashboards/overview`)로 rerender → 서브메뉴 사라지고 WMS가 토글 버튼으로 복귀, `개요`(nav-dashboards)가 보이고 `aria-current="page"`.
   - 한 부모 라우트(`/wms/outbound`)에서 다른 부모 라우트(`/erp/orgview`)로 rerender → WMS drill이 ERP drill로 교체(잔류 없음).

## Out of Scope (의도적 유지)
- 수동 펼침/접기 보존(라우트 변경 없는 상호작용) — 회귀 없음.
- 부모 라우트 간·부모 자식 라우트 deep-link 자동 열림(FE-059/076/077/078 기존 동작) — 그대로.
- 스크롤: Next.js `<Link>` 기본 `scroll` 동작으로 페이지 상단 이동은 이미 처리됨(변경 없음).
- contract/producer/스펙 변경 없음(순수 클라이언트 UI 상태 로직).

## Acceptance Criteria
- **AC-1** WMS/ERP/… 하위 메뉴에 drill-in 한 상태에서 "Platform Console" 브랜드 링크를 누르면 `/dashboards/overview`로 이동하면서 사이드바가 최상위 목록으로 복귀하고 `개요`가 강조된다.
- **AC-2** 부모 라우트 간 이동은 새 부모 drill로 정확히 교체된다(잔류 drill 없음). 부모 자식 라우트 deep-link 자동 열림은 회귀 없음.
- **AC-3** 수동 펼침/접기(라우트 변경 없는 토글)는 그대로 보존된다.
- **AC-4** `pnpm lint` + `tsc --noEmit` + `vitest run`(sidebar-drilldown 포함) green.

## Edge Cases
- 카탈로그(`/console`)·감사(`/audit`)처럼 비-drill 최상위 leaf로 이동 → 최상위 목록 유지(초기화 무해).
- deep-link로 부모 자식 라우트 직접 진입 → 초기 `useState`가 부모를 열고, effect도 같은 값으로 동기화(멱등).

## Failure Scenarios
- 가드 제거가 수동 상호작용을 깨뜨릴 위험 → effect deps가 `[pathname]`뿐이라 라우트 변경 시에만 발화, 수동 토글(라우트 불변)은 미발화로 보존됨. 테스트가 rerender(라우트 변경)로만 초기화됨을 가드.

## Related Specs / Contracts
- 없음 — 순수 console-web 클라이언트 UI 상태 로직. `specs/services/console-web/architecture.md`의 네비게이션 서술과 모순 없음(최상위 복귀는 drill 모델의 자연스러운 보정).
