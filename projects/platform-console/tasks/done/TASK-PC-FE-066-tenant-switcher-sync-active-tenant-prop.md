# Task ID

TASK-PC-FE-066

# Title

console-web 상단 TenantSwitcher: 외부에서 바뀐 활성 테넌트(prop)를 로컬 선택 state에 동기화 (카탈로그 테넌트 클릭 후 상단 선택창 stale 버그 수정)

# Status

done

# Owner

frontend (Opus 4.8 analysis / Sonnet 4.6 impl) — 1-line derived-state sync fix. No contract/spec/backend change.

# Task Tags

- code
- test

---

# Dependency Markers

- **user report (2026-06-09)** — PC-FE-065 카탈로그에서 카드 안 테넌트를 클릭하면 활성 테넌트가 바뀌고(`/api/tenant`) 그 제품 도메인 운영으로 이동하지만, **상단 헤더의 테넌트 선택창은 이전 값 그대로** 남는다.
- **root cause** — `TenantSwitcher`는 `(console)` 레이아웃에 있어 네비게이션/`router.refresh()` 후에도 언마운트되지 않는다. 선택값을 `useState(activeTenant ?? '')` 로컬 state로만 들고 있어, 마운트 후 `activeTenant` prop이 새 값으로 바뀌어도 `selected`가 갱신되지 않는다(파생 state 미동기화). 자기 드롭다운 `onChange`로 바꿀 때만 `setSelected` 호출 → 외부 변경(카탈로그)이 반영 안 됨.
- **relates**: TASK-PC-FE-065(카탈로그 테넌트 클릭=활성 설정+네비). TASK-PC-FE-040(switch onSuccess=router.refresh).

# Goal

`TenantSwitcher`에 `useEffect(() => setSelected(activeTenant ?? ''), [activeTenant])`를 추가해, 외부(카탈로그 등)에서 활성 테넌트가 바뀌어 서버 레이아웃이 새 `activeTenant` prop을 내려주면 상단 선택창이 그 값으로 동기화되게 한다. 직접 onChange 경로는 불변(즉시 setSelected + mutate, refresh 후 prop=동일값이라 idempotent).

# Scope

## In Scope

- **`src/features/tenant/components/TenantSwitcher.tsx`** — `useEffect` import 추가; `useState` 직후(조건부 return 이전, hooks 규칙 준수) prop→state 동기화 effect 추가. 그 외 렌더/onChange/degrade 로직 불변.
- **Tests** — `tests/unit/TenantSwitcher.test.tsx`: `activeTenant` prop이 외부에서 바뀌면(rerender) `select.value`가 새 값으로 갱신됨을 단언(현 stale 동작이면 fail → fix로 green). 기존 7 케이스 무회귀.

## Out of Scope

- 카탈로그/CatalogGrid/ServiceTile(PC-FE-065 동작 불변 — 이미 활성+네비는 정상).
- use-tenant-switch 훅(불변), 백엔드/계약/registry.

# Acceptance Criteria

- [ ] `activeTenant` prop을 `"acme-corp"`로 마운트 후 `"globex-corp"`로 rerender → `tenant-select`의 value가 `"globex-corp"`로 갱신.
- [ ] `activeTenant`가 `null`→tenant로 바뀌면 placeholder→해당 tenant로 갱신; tenant→`null`이면 placeholder로 복귀.
- [ ] 직접 onChange 선택/스위치/refresh/invalidate(기존 7 케이스) 전부 무회귀.
- [ ] `pnpm exec vitest run` green, `npx tsc --noEmit` clean; scope = console-web only.

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` §2.7 (active-tenant switch) — 소비만.

# Related Contracts

- 변경 없음.

# Target Service

- `platform-console` / `apps/console-web` — `features/tenant` + 테스트.

# Architecture

- 파생 state 동기화: 단일 진실원천은 서버가 내려주는 `activeTenant`(active-tenant 쿠키 기반). 로컬 `selected`는 optimistic UX용이되 prop이 바뀌면 prop을 따라간다. 외부 변경(카탈로그 클릭→쿠키 set + refresh/navigate) 시 레이아웃 재렌더로 prop이 갱신되고 effect가 state를 맞춘다.

# Edge Cases

- 동일 값으로 prop 재전달 → setSelected 동일값(no-op 렌더). 문제 없음.
- 단일/제로 테넌트 → 기존 early-return 유지(effect는 hooks 규칙상 return 이전에 위치하므로 항상 호출됨).
- onChange로 바꾼 직후 refresh로 prop이 같은 값이 되어 돌아옴 → idempotent.

# Failure Scenarios

- effect 누락 → 외부 변경이 상단 선택창에 반영 안 됨(보고된 버그). AC-1이 이를 게이트.
- effect 의존성에 `activeTenant` 누락 → 최초 1회만 동기화. AC가 rerender 단언으로 적발.

# Definition of Done

- [ ] prop→state 동기화 effect + 무회귀
- [ ] vitest + tsc green; scope = console-web only
- [ ] Acceptance Criteria 충족
- [ ] Ready for review
