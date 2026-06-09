# Task ID

TASK-PC-FE-067

# Title

console-web 카탈로그 테넌트 클릭: SPA router.push → 하드 네비게이션(window.location.assign)으로 교체 — 도메인 운영 진입 시 상단 TenantSwitcher 결정적 갱신

# Status

review

# Owner

frontend (Opus 4.8 analysis / Sonnet 4.6 impl) — fixes a runtime gap PC-FE-066's useEffect could not close. No contract/spec/backend change.

# Task Tags

- code
- test

---

# Dependency Markers

- **user report (2026-06-09)** — PC-FE-065 카탈로그에서 카드 안 테넌트를 클릭하면 그 제품 도메인 운영으로는 이동하지만 **상단 테넌트 선택창이 여전히 안 바뀐다**(PC-FE-066 useEffect 머지·라이브 후에도 미해결).
- **root cause (확정)** — active-tenant는 **httpOnly 쿠키**(`console_active_tenant`, `session.ts`)라 클라이언트 `TenantSwitcher`는 직접 못 읽고 **서버 `(console)` 레이아웃이 prop으로 내려줘야만** 갱신된다. 그러나 **Next.js App Router는 공유 레이아웃을 클라이언트 네비게이션 간 재렌더하지 않으며**, 카탈로그 타일 `<Link>` 프리페치/Router Cache의 목적지 RSC는 **이전 테넌트 쿠키로 렌더**돼 있다 → `router.push(productRoute)`는 stale 레이아웃(옛 `activeTenant` prop)을 재사용 → switcher가 받을 새 prop 자체가 안 옴(PC-FE-066 useEffect는 prop이 바뀌어야 동작하므로 무력). `router.refresh()`와의 순서도 racy.
- **mechanism** — `/api/tenant`는 쿠키를 응답에 atomically set(route.ts). 따라서 전환 성공 후 **하드 네비게이션**(`window.location.assign`)으로 목적지를 풀로드하면 레이아웃 서버 컴포넌트가 **새 쿠키로 재실행** → 도메인 운영 진입 + 상단 switcher 동시 결정적 갱신(Router-Cache/refresh-순서 레이스 제거).
- **relates/builds-on**: TASK-PC-FE-065(테넌트 클릭=활성+네비), TASK-PC-FE-066(switcher prop 동기화 useEffect — 유지; SPA 자기-변경/미래 경로 방어용, 본 fix 후 카탈로그 경로엔 무관해지나 무해).

# Goal

`CatalogGrid.onSelectTenant`를 `switchTenant.mutate(tenant, { onSuccess: () => window.location.assign(productRoute) })`로 변경(기존 `router.push` 제거, `useRouter` import 제거). 테넌트 전환은 기존 assume-tenant 흐름 그대로, 성공 시 하드 네비게이션으로 목적지 도메인 운영 진입 → 상단 switcher가 새 활성 테넌트로 갱신.

# Scope

## In Scope

- **`src/features/catalog/components/CatalogGrid.tsx`** — `useRouter` import/사용 제거; `onSelectTenant` = `switchTenant.mutate(tenant, { onSuccess: () => window.location.assign(productRoute) })`. 주석으로 하드-네비 사유(httpOnly 쿠키 + 공유 레이아웃 비-재렌더 + 프리페치 stale) 명시. 렌더/필터-없음/dot 로직 불변.
- **Tests** — `tests/unit/catalog-grid.test.tsx`: `pushMock`/`next/navigation` mock 제거, `window.location.assign` spy로 교체; 테넌트 클릭 시 `mutate(tenant,{onSuccess})` + onSuccess가 `window.location.assign('/wms')` 호출 단언. 전체 목록 유지/헤더·테넌트 dot 케이스 불변.

## Out of Scope

- `TenantSwitcher`(PC-FE-066 useEffect 유지 — 제거하지 않음), 상단 드롭다운 자기-변경 경로(이미 정상: 동일 라우트 refresh).
- 사이드바/카탈로그 필터/제품명/백엔드/계약/registry.
- httpOnly 쿠키를 non-httpOnly로 바꾸거나 client store 도입(아키텍처 변경 — 불필요).

# Acceptance Criteria

- [ ] 카탈로그 카드 안 테넌트 버튼 클릭 → `useTenantSwitch.mutate(tenant, {onSuccess})` 호출 + 성공 시 `window.location.assign(resolveConsoleRoute(product))` 호출(SPA `router.push` 아님).
- [ ] 카탈로그는 여전히 전체 제품 표시(필터 없음) + 헤더 dot/테넌트 앞 dot 불변(무회귀).
- [ ] 라이브에서 카드 테넌트 클릭 → 그 제품 도메인 운영 화면 진입 + **상단 테넌트 선택창이 그 테넌트로 갱신**.
- [ ] `pnpm exec vitest run` green, `npx tsc --noEmit` clean; scope = console-web only.

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` §2.7 (active-tenant switch) — 소비만.

# Related Contracts

- 변경 없음.

# Target Service

- `platform-console` / `apps/console-web` — `features/catalog` + 테스트.

# Architecture

- active-tenant = httpOnly 쿠키(서버만 읽음) → 상단 switcher는 서버 레이아웃 prop이 단일 진실원천. Next 공유 레이아웃이 클라 네비게이션 간 재렌더 안 되므로, 테넌트를 바꾸며 다른 라우트로 갈 땐 **풀로드**가 레이아웃 재실행을 보장하는 결정적 수단. (동일 라우트 머무름=상단 드롭다운 경로는 `router.refresh()`로 충분 — 거긴 불변.)

# Edge Cases

- 전환 실패(403/422/503) → onSuccess 미실행 → 네비게이션 없음(현 위치 유지, switcher 불변). 기존 fail-closed 보존.
- iam 카드 테넌트 클릭 → `/accounts`(IAM 도메인 운영)로 풀로드.
- coming-soon(available:false) → 테넌트 버튼 없음(불변).

# Failure Scenarios

- 여전히 `router.push` 사용 → 상단 switcher stale(보고된 버그). AC-1이 `window.location.assign` 단언으로 게이트.
- 하드-네비를 onSuccess 밖(전환 전)에서 호출 → stale-tenant로 풀로드. AC가 onSuccess 안에서만 단언.

# Definition of Done

- [ ] router.push → window.location.assign(onSuccess) + useRouter 제거
- [ ] vitest + tsc green, 무회귀; scope = console-web only
- [ ] 라이브 재배포 후 상단 switcher 갱신 확인
- [ ] Acceptance Criteria 충족
- [ ] Ready for review
