# Task ID

TASK-PC-FE-065

# Title

console-web 카탈로그 revise: 필터 제거(전체 표시) + 카드 내 테넌트 클릭 시 그 테넌트의 도메인 운영 화면으로 이동 + 테넌트 앞에도 도메인 상태 dot

# Status

done

# Owner

frontend (Opus 4.8 analysis / Sonnet 4.6 impl) — revises TASK-PC-FE-064's catalog interaction per user. No contract/spec/backend change.

# Task Tags

- code
- test

---

# Dependency Markers

- **user request (2026-06-09)** — PC-FE-064 동작 변경:
  1. 테넌트에 따라 가능한 서비스만 필터링하지 말고 **전체 제품을 항상 표시**.
  2. 카드 안의 테넌트를 클릭하면 (필터가 아니라) **그 테넌트를 활성으로 설정하고 그 제품(도메인)의 운영 화면으로 이동**(`resolveConsoleRoute(product)` — wms→/wms 운영, scm→/scm, iam→/accounts …).
  3. 상태 dot 2레벨: **제품 카드 타이틀 앞**(도메인 상태) **+ 각 테넌트 앞**(그 테넌트에 대한 도메인 상태 = 제품의 도메인 상태, 전역이라 동일 tone).
- **revises**: TASK-PC-FE-064 (헤더 dot 유지; 테넌트 필터 → 테넌트 네비게이션으로 교체; 테넌트별 dot 추가).
- **mechanism**: 활성 설정 = 기존 `useTenantSwitch`(/api/tenant assume-tenant). 이동 = switch 성공 후 `router.push(productRoute)`.

# Goal

`CatalogGrid`에서 **필터 제거**(항상 전 제품 표시). 테넌트 버튼 클릭 → `useTenantSwitch.mutate(tenant, { onSuccess: () => router.push(productRoute) })`(활성 설정 후 제품 운영 화면으로 이동). `ServiceTile`은 제품 헤더 dot(유지) + **각 테넌트 버튼 앞에도 같은 tone dot**. 제품의 라우트(`resolveConsoleRoute(product)`)는 테넌트 버튼이 ServiceTile에서 전달.

# Scope

## In Scope

- **`src/features/catalog/components/CatalogGrid.tsx`** — `filterTenant` state/배너/"전체 보기"/empty-filter 제거(항상 `products` 전체 렌더). `useRouter` 추가. `onSelectTenant(tenant, productRoute)` = `switchTenant.mutate(tenant, { onSuccess: () => router.push(productRoute) })`. `activeTenant` prop은 더 이상 필터 초기화에 쓰지 않음(제거 또는 무시).
- **`src/features/catalog/components/ServiceTile.tsx`** — `onSelectTenant?: (tenant: string, productRoute: string) => void`로 시그니처 변경; 테넌트 버튼 `onClick={() => onSelectTenant?.(tenant, href)}`. 각 테넌트 버튼 앞에 `tone` dot(`tile-{key}-tenant-{t}-status`, data-tone) — 제품 도메인 상태(전역이라 헤더와 동일). 헤더 dot 유지.
- **`src/features/catalog/components/ServiceCatalog.tsx`** / **`console/page.tsx`** — `activeTenant` prop 전달은 유지 가능하나 필터에 미사용(시그니처 호환 위해 optional 유지 또는 정리). healthByDomain/page 합성 불변.
- **Tests**:
  - `tests/unit/catalog-grid.test.tsx` (rewrite) — 필터 테스트 제거; **항상 전체 표시**(테넌트 클릭해도 타일 숨지 않음) + 테넌트 클릭 시 `mutate(tenant, {onSuccess})` 호출 + onSuccess가 `router.push(productRoute)` 호출 단언(mutate mock이 onSuccess 실행).
  - `tests/unit/catalog-tenant-list.test.tsx` (update) — 테넌트 버튼 클릭이 `onSelectTenant(tenant, '/wms')`로 호출 + 테넌트 앞 dot(`tile-wms-tenant-acme-status` tone) + 헤더 dot 유지.
  - `tests/unit/ServiceCatalog.test.tsx` / `catalog-route.test.tsx` — 기존 provider 래핑 유지(불변 단언).

## Out of Scope

- 제품 displayName(별 task BE-340).
- domain health를 테넌트별로 만드는 것(전역 유지 — 테넌트 dot = 제품 도메인 상태).
- 백엔드/계약/registry 변경; 사이드바.

# Acceptance Criteria

- [ ] 카탈로그는 활성 테넌트와 무관하게 **항상 전체 제품** 표시(필터/“전체 보기” 배너 없음).
- [ ] 테넌트 버튼 클릭 → 활성 테넌트 설정(`useTenantSwitch.mutate`) + 성공 시 그 제품의 `resolveConsoleRoute` 라우트로 `router.push`(클릭은 타일을 숨기지 않음).
- [ ] 제품 헤더 dot(도메인 상태) + 각 테넌트 앞 dot(같은 tone) 표시; health 없으면 양쪽 dot 생략.
- [ ] `pnpm exec vitest run` green(rewrite/update + 기존 무회귀), `npx tsc --noEmit` clean; scope = console-web only.

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` §2.2 / §2.7(active-tenant switch) — 소비만.

# Related Contracts

- 변경 없음.

# Target Service

- `platform-console` / `apps/console-web` — catalog(CatalogGrid/ServiceTile) + 테스트.

# Architecture

- 테넌트 선택 = 기존 assume-tenant flow + 성공 후 제품 운영 화면 네비게이션(`switchTenant.mutate(..., { onSuccess })`로 순서 보장). 필터 제거로 CatalogGrid 단순화(상태 없음, 단 switch/router용 client 유지). 테넌트 dot = 제품 도메인 상태(전역).

# Edge Cases

- health 없음 → 헤더·테넌트 dot 모두 생략(카탈로그 정상).
- iam 카드 테넌트 클릭 → /accounts(IAM 도메인 운영=계정 운영)로 이동.
- coming-soon(available:false) 타일 → dot/테넌트 버튼 없음(불변).

# Failure Scenarios

- 테넌트 클릭이 여전히 필터링 → AC가 "항상 전체 표시 + 네비게이션" 단언.
- 네비게이션이 활성 설정 전에 발생(stale tenant) → `onSuccess` 후 push 로 순서 보장 단언.

# Definition of Done

- [ ] 필터 제거(전체 표시) + 테넌트 클릭=활성+도메인 운영 이동 + 헤더/테넌트 dot
- [ ] vitest + tsc green, 무회귀; scope = console-web only
- [ ] Acceptance Criteria 충족
- [ ] Ready for review
