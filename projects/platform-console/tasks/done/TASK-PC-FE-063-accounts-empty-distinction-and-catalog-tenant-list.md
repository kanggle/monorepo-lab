# Task ID

TASK-PC-FE-063

# Title

console-web UX clarity: (1) 계정 운영 empty state — distinguish 검색 결과 없음 vs 조회 권한 없음 (as far as the backend allows); (2) 카탈로그 tile — list available tenants one per line under the count

# Status

done

# Owner

frontend (Opus 4.8 analysis / Sonnet 4.6 impl) — two small console-web copy/UX refinements; no contract/spec/backend change.

# Task Tags

- code
- test

---

# Dependency Markers

- **user request (2026-06-09)**: (1) 계정 운영(`/accounts`)의 빈 상태 메시지 "표시할 계정이 없습니다. (검색 결과 없음 또는 조회 권한 없음)" 가 두 경우를 합쳐 hedging → 구분; (2) 카탈로그 타일의 "N개 테넌트 이용 가능" 아래에 이용 가능한 테넌트를 한 줄에 하나씩 나열.
- **backend constraint (계약 §2.4.1, `accounts-state.ts:18`)**: producer는 `account.read` 미부여 시 **403이 아니라 빈 200 페이지**를 반환 → 데이터만으론 "권한 없음"과 "계정 0개"가 동일. 따라서 순수 구분 가능한 신호는 **(a) 클라이언트 재조회 403/`PERMISSION_DENIED`** + **(b) 검색 필터(`query.email`) 활성 여부**뿐. 완전한 권한-전용 구분은 백엔드 신호(별 task)가 필요 — 본 task는 신호 가능한 범위에서 정직하게 구분.

# Goal

**(1) 계정 운영 빈 상태** — `accounts-empty` 메시지를 다음으로 분기:
- 클라이언트 조회 에러가 **403/`PERMISSION_DENIED`** → `조회 권한이 없습니다.`
- 그 외 조회 에러 → `목록을 불러올 수 없습니다.`
- 에러 아님 + **검색 필터 활성**(`query.email`) + 0건 → `검색 결과가 없습니다.`
- 에러 아님 + **무필터** + 0건 → `조회 권한이 없거나 등록된 계정이 없습니다.` (producer가 권한없음/계정0을 빈 200으로 합쳐 반환 — 정직한 union)

분류는 순수 함수 `classifyAccountsEmpty()`로 추출해 단위 테스트. `data-empty-reason` 속성으로 사유 노출.

**(2) 카탈로그 타일** — `available` 타일에서 `tenants.length > 0` 일 때 "N개 테넌트 이용 가능" + **테넌트를 한 줄에 하나씩 `<ul>`** 로 나열(`tile-{key}-tenant-{t}`). 0개면 기존 "이용 가능" 유지.

# Scope

## In Scope

- **`src/features/accounts/lib/classify-empty.ts`** (new) — `export type AccountsEmptyReason = 'forbidden' | 'load-error' | 'no-results' | 'forbidden-or-empty'`; `export function classifyAccountsEmpty(isError, error, searching): { reason, message }` (위 4분기; 403/PERMISSION_DENIED 판정은 `ApiError.status`/`.code`).
- **`src/features/accounts/components/AccountsScreen.tsx`** — 빈 상태 `<p data-testid="accounts-empty">` 가 `classifyAccountsEmpty(search.isError, search.error, !!query.email)` 결과의 `message` 렌더 + `data-empty-reason={reason}`. 기존 lumped 문자열 제거.
- **`src/features/catalog/components/ServiceTile.tsx`** — available 타일에서 tenants>0 시 count line + per-tenant `<ul>`(한 줄 1개, `data-testid` `tile-{key}-tenants` / `tile-{key}-tenant-{t}`). HTML: `<ul>`/`<li>`는 `<a>`(Link) 내부 허용(non-interactive).
- **Tests**:
  - `tests/unit/accounts-empty-state.test.ts` (new) — `classifyAccountsEmpty` 4분기: 403 ApiError→forbidden, 일반 에러→load-error, searching+빈→no-results, 무필터+빈→forbidden-or-empty.
  - `tests/unit/catalog-tenant-list.test.tsx` (new) — ServiceTile를 tenants=[a,b]로 렌더 → 각 테넌트 한 줄(li) + count line 존재; tenants=[] → "이용 가능"만.

## Out of Scope

- 백엔드/계약 변경(producer가 권한없음을 403/플래그로 명시하는 "순수 권한 구분"은 별도 cross-cutting task — 본 task는 신호 가능 범위만).
- 계정 운영의 다른 상태(no-tenant 게이트, degraded 배너)·검색 폼·뮤테이션.
- 카탈로그의 `available:false` "Coming soon" 타일, registry/routing.

# Acceptance Criteria

- [ ] 계정 운영 빈 상태: 403/PERMISSION_DENIED→"조회 권한이 없습니다.", 일반 조회에러→"목록을 불러올 수 없습니다.", 검색중+0건→"검색 결과가 없습니다.", 무필터+0건→"조회 권한이 없거나 등록된 계정이 없습니다."; `data-empty-reason` 정확.
- [ ] `classifyAccountsEmpty` 순수함수로 4분기 단위 테스트 green.
- [ ] 카탈로그 available 타일(tenants>0): "N개 테넌트 이용 가능" + 테넌트 한 줄에 하나씩(li); tenants=0이면 "이용 가능".
- [ ] `pnpm exec vitest run` green(new + 기존 무회귀), `npx tsc --noEmit` clean; scope = console-web only.

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` §2.4.1 (accounts list; account.read 미부여→빈 페이지) / §2.2 (registry product tenants).

# Related Contracts

- 변경 없음(소비측 copy/UX만).

# Target Service

- `platform-console` / `apps/console-web` — accounts(빈상태 분류 lib + 화면) + catalog(타일) + 단위 테스트. read-only/copy.

# Architecture

- 분류 로직을 순수함수로 추출(테스트 용이 + 화면은 message/reason만 소비). 카탈로그 타일은 registry `tenants[]`를 그대로 나열(데이터-드리븐 유지).

# Edge Cases

- 계정 운영: 클라 403(드묾, 대부분 빈 200)→pure "조회 권한 없음"; 무필터+빈=권한없음∪계정0(정직 union); 검색중+빈=검색결과없음.
- 카탈로그: tenants 1개→그 1줄; 다수→각 줄; 0개→"이용 가능"(목록 없음).

# Failure Scenarios

- 무필터+빈을 "검색 결과 없음"으로 오표기 → AC가 검색필터 활성 여부 분기 단언.
- 타일 테넌트 목록이 Link 내부라 hydration/HTML 위반 → `<ul>`/`<li>`는 flow content(허용); a11y lint green 확인.

# Definition of Done

- [ ] 빈 상태 4분기 + 카탈로그 per-tenant 목록 동작
- [ ] vitest + tsc green, 무회귀; scope = console-web only
- [ ] Acceptance Criteria 충족
- [ ] Ready for review
