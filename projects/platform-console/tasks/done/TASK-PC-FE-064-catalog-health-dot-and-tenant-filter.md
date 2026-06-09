# Task ID

TASK-PC-FE-064

# Title

console-web 카탈로그: (1) 제품 헤더에 도메인 상태 dot 표시; (2) 카탈로그의 테넌트를 클릭하면 활성 테넌트로 설정하고 카탈로그를 그 테넌트의 도메인만 필터

# Status

done

# Owner

frontend (Opus 4.8 analysis / Sonnet 4.6 impl) — catalog cross-feature 합성(catalog + domain-health + tenant switch); client 필터/선택 그리드. no contract/spec/backend change.

# Task Tags

- code
- test

---

# Dependency Markers

- **user request + AskUserQuestion 결정(2026-06-09)**: (1) 상태 dot 위치 = **제품 헤더에 1개**(도메인 상태는 도메인=제품 단위 전역이라 테넌트별 반복 대신 제품당 1개); (2) 테넌트 선택 동작 = **활성 테넌트 설정 + 카탈로그를 그 테넌트의 도메인만 필터**.
- **builds on**: PC-FE-061(domain health 요약, `getDomainHealthState`) + PC-FE-063(타일 테넌트 목록) + 기존 tenant switch(`useTenantSwitch` → `/api/tenant` assume-tenant + `router.refresh`).
- **facts**: `ProductKey(iam/wms/scm/erp/finance)` ↔ domain-health `CARD_ORDER` 1:1 → productKey로 health tone 조회. domain health는 active tenant gate(없으면 dot 미표시, degrade).

# Goal

**(1) 상태 dot** — 카탈로그 page에서 `getDomainHealthState()`를 함께 fetch → `healthByDomain: Partial<Record<ProductKey, HealthTone>>` 구성 → 각 available 제품 타일 **헤더(제품명 옆)에 tone dot 1개**(정상=green/주의=red/점검불가=gray; health 없으면 dot 생략). 테넌트 줄엔 반복 안 함.

**(2) 테넌트 필터+선택** — 타일의 테넌트가 **버튼**. 클릭 시: (a) `useTenantSwitch.mutate(tenant)`로 활성 테넌트 설정(기존 `/api/tenant` flow), (b) 카탈로그 그리드를 **그 테넌트를 포함하는 제품(`product.tenants.includes(tenant)`)만** 필터. 필터 활성 시 "테넌트 X의 도메인" 배너 + **"전체 보기"**(필터 해제). 필터 초기값 = 현재 활성 테넌트.

# Scope

## In Scope

- **`src/features/domain-health/lib/tone.ts`** (new) — `export type HealthTone = 'healthy'|'attention'|'unknown'`; `export function healthTone(card): HealthTone` (`degraded`→unknown, `ok`+`UP`→healthy, else attention). `DomainHealthSummaryCard.tsx`의 local toneOf를 이 helper로 교체(DRY, data-tone 불변).
- **`src/features/catalog/components/CatalogGrid.tsx`** (new, `'use client'`) — props `{ products, healthByDomain, activeTenant }`. `filterTenant` state(init=activeTenant), `useTenantSwitch`. 필터 배너+전체보기, 필터된 타일 렌더, 각 타일에 `tone`+`onSelectTenant` 전달. 필터 결과 0개 → "이 테넌트에 이용 가능한 도메인이 없습니다." (`catalog-tenant-filter`/`catalog-filter-clear`/`catalog-filter-empty` testid).
- **`src/features/catalog/components/ServiceTile.tsx`** (`'use client'`, presentational, no hooks) — props에 `tone?: HealthTone` + `onSelectTenant?: (t)=>void`. available 타일 구조 변경: 헤더 `<Link href>`(제품명 + tone dot + count) + 그 아래 테넌트 **버튼** 목록(`onSelectTenant`). `tile-{key}-tenant-{t}`는 이제 button. `available:false`(coming soon) 타일·dot/버튼 없음(기존 유지).
- **`src/features/catalog/components/ServiceCatalog.tsx`** — props `{ catalog, healthByDomain?, activeTenant? }`; 제품 그리드를 `<CatalogGrid>`로 위임(heading/degraded/empty branch는 그대로 server). 새 props optional(기존 호출/테스트 호환).
- **`src/app/(console)/console/page.tsx`** — `getDomainHealthState()` + `getActiveTenant()` 병행 fetch → `healthByDomain` 구성 → `<ServiceCatalog catalog healthByDomain activeTenant />`. health degrade는 dot 생략으로만 흡수(카탈로그 blank 금지).
- **Tests**:
  - `tests/unit/domain-health-tone.test.ts` (new) — `healthTone` 3분기.
  - `tests/unit/catalog-grid.test.tsx` (new) — 테넌트 클릭→필터(그 테넌트 포함 제품만)+switchTenant 호출(mock)+전체보기 해제; 헤더 dot(tone) 렌더; 필터-0 상태. (QueryClientProvider + next/navigation useRouter mock + next/link mock)
  - `tests/unit/catalog-tenant-list.test.tsx` (update) — ServiceTile new props(tone + onSelectTenant stub): 테넌트가 button + 클릭 시 onSelectTenant 호출 + 헤더 dot.
  - `tests/unit/ServiceCatalog.test.tsx` (update) — 제품 렌더 케이스를 QueryClientProvider + router/link mock으로 래핑(CatalogGrid의 client hook). 기존 단언(tile/link href/coming-soon/degraded/empty) 유지.

## Out of Scope

- domain health를 테넌트별로 만드는 것(전역 유지 — 별건). 백엔드/계약 변경.
- top-bar tenant switcher 변경(카탈로그는 동일 `/api/tenant` 재사용).
- registry/routing(`resolveConsoleRoute`) 변경.

# Acceptance Criteria

- [ ] available 제품 타일 헤더에 도메인 상태 tone dot 1개(health 있을 때); 테넌트 줄엔 dot 반복 없음; health 미가용 시 dot 생략(카탈로그 정상).
- [ ] 타일 테넌트는 버튼; 클릭 시 `useTenantSwitch.mutate(tenant)` 호출(활성 설정) + 그리드가 그 테넌트 포함 제품만 표시; "전체 보기"로 해제; 필터 결과 0 → 안내 문구.
- [ ] 헤더 제품명은 여전히 `resolveConsoleRoute` 목적지로 네비(링크 유지); coming-soon 타일 비상호작용 유지.
- [ ] `pnpm exec vitest run` green(new + updated, 무회귀), `npx tsc --noEmit` clean; scope = console-web only.

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` §2.2(registry product/tenants) / §2.4.9.2(domain health) / §2.7(active-tenant switch) — 소비만.

# Related Contracts

- 변경 없음.

# Target Service

- `platform-console` / `apps/console-web` — catalog(ServiceCatalog/ServiceTile/CatalogGrid) + domain-health tone lib + console page 합성 + 테스트.

# Architecture

- 서버 page가 catalog+domain-health+activeTenant fetch(병행) → server presentational `ServiceCatalog` → client `CatalogGrid`(필터 state + tenant switch) → presentational client `ServiceTile`(handler/tone props, no hooks). domain health tone는 공유 `healthTone()`로 DRY. 필터는 client state(필터 해제=전체보기), 활성 설정은 기존 assume-tenant flow.

# Edge Cases

- 활성 테넌트 없음 → dot 없음 + 필터 없음(전체); 테넌트 클릭→활성 설정+필터 동시.
- 필터 테넌트에 포함 제품 0 → "이 테넌트에 이용 가능한 도메인이 없습니다."
- coming-soon(available:false, tenants[]=빈) → 필터 시 제외, dot/버튼 없음.
- health degraded/null → 해당 dot 생략(전역 degrade도 카탈로그 비우지 않음).

# Failure Scenarios

- 테넌트 버튼을 Link 내부(인터랙티브 중첩) → 무효 HTML → 헤더 Link와 테넌트 버튼 분리 단언.
- 필터가 활성 테넌트를 영구 가둠 → "전체 보기" 해제 단언.
- ServiceCatalog client hook이 provider 없이 테스트 깨짐 → 테스트 provider 래핑.

# Definition of Done

- [ ] 헤더 dot + 테넌트 클릭 필터/활성 설정 + 전체보기 동작
- [ ] vitest + tsc green, 무회귀; scope = console-web only
- [ ] Acceptance Criteria 충족
- [ ] Ready for review
