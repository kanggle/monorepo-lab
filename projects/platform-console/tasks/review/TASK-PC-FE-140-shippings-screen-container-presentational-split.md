# Task ID

TASK-PC-FE-140

# Title

console-web `ecommerce-ops/ShippingsScreen` 컨테이너/프레젠테이셔널 분할: 필터·쿼리·상태 전이/추적/배송 뮤테이션 로직을 `useShippingsScreen` 훅으로, 목록 테이블+페이지네이션을 `ShippingsTable` 프레젠테이셔널 컴포넌트로, 상태 라벨을 `shipping-labels`로 추출 — render 출력·test-id·wire shape 불변(behavior-preserving)

# Status

review

# Owner

frontend (Opus 4.8 분석 / 구현 권장=Sonnet 4.6 또는 Opus — behavior-preserving 컴포넌트 분할, contract/spec/backend 무변경)

# Task Tags

- code
- test
- refactor

---

# Dependency Markers

- **builds on**: TASK-PC-FE-088(ShippingsScreen 도입 — § 2.4.10.3 배송 운영 목록) + TASK-PC-FE-121(shipping schema nullable) + TASK-PC-FE-129(shipping mutation 응답 schema). 본 분할이 보존해야 하는 동작·test-id·상태머신(`allowedNextStatus`)의 출처.
- **note (현 구조)**: [`ShippingsScreen.tsx`](../../apps/console-web/src/features/ecommerce-ops/components/ShippingsScreen.tsx)(435줄)는 단일 `'use client'` 컴포넌트가 (a) 상태 필터 + 페이지네이션 쿼리 + seeded 서버렌더 fallback, (b) forbidden/degraded/loading 파생, (c) 3종 뮤테이션(상태 전이·배송 시작[carrier+trackingNumber]·추적 동기화)과 confirm/ShipFormDialog + 에러 매핑, (d) 헤딩·필터 폼·목록 테이블·페이지네이션·2 다이얼로그 전체 마크업을 모두 보유한다. 로직과 렌더가 한 파일에 응집돼 단일 책임 위반·테스트 표면 비대(PC-FE-101/103 분할 대상과 동형).
- **pattern**: container/presentational + 커스텀 훅 추출(PC-FE-101 `ApprovalScreen` / PC-FE-106 `useLedgerOpsState`와 동일 계열).

# Goal

`ShippingsScreen`을 **로직(훅) / 표현(테이블) / 라벨(상수) / 얇은 컨테이너** 계층으로 분리해 가독성·테스트 용이성을 높인다. 동작은 완전 불변:

- `useShippingsScreen(shippings)` — 필터/페이지네이션 쿼리 상태, seeded fallback, forbidden/degraded/loading 파생, 3종 뮤테이션(`updateStatus`/`refreshTracking`)과 confirm 전이/ShipFormDialog 상태·핸들러(`openTransition`/`confirmTransition`/`confirmShip`/`triggerRefresh`/`submitFilter`), `isAnyPending` 공유 가드, 페이지네이션 파생(prev/next disabled·pageInfo·onPrev/onNext)을 소유. 로직은 분할 전과 1:1 동일(seeded 분기·degrade 조건·wire body 포함).
- `ShippingsTable` — 목록 `<table>` + 페이지네이션 `<nav>`를 표현 전용으로 렌더. 행별 전이/추적 버튼은 `isAnyPending`으로 게이트, 상태 소유권은 훅(부모)에 유지하고 props로만 받음.
- `shipping-labels` — `STATUS_FILTER_OPTIONS` / `statusLabel` / `nextStatusLabel` (순수 표시 상수; 컨테이너 필터·테이블·confirm 다이얼로그 공용).
- `ShippingsScreen` — 훅 호출 + 헤딩/필터 폼 + forbidden/degraded/loading/empty/table 분기 + 2 다이얼로그 배선만 남는 컨테이너.

behavior-preserving: 렌더 출력·DOM 구조·**모든 `data-testid`**·ARIA(section/search/nav label)·상태머신 전이 분기·degrade/forbidden/loading/empty 분기·confirm vs ShipFormDialog 분기·PATCH wire body(특히 `deductWmsInventory`는 true일 때만 전송)·페이지네이션 계산은 전부 기존과 동일.

# Scope

## In Scope

- **신규 `src/features/ecommerce-ops/components/shipping-labels.ts`** — `STATUS_FILTER_OPTIONS`, `STATUS_LABELS`/`statusLabel`, `NEXT_STATUS_LABELS`/`nextStatusLabel`를 그대로 이전(컨테이너·테이블·다이얼로그가 동일 헬퍼 참조, 중복 없음).
- **신규 `src/features/ecommerce-ops/components/use-shippings-screen.ts`** — 상태/쿼리/파생/뮤테이션/핸들러/페이지네이션을 그대로 이전. confirm 전이 description·confirmLabel 문자열 파생도 포함(다이얼로그 렌더 단순화). 반환 객체로 컨테이너·테이블이 필요한 값/핸들러 노출.
- **신규 `src/features/ecommerce-ops/components/ShippingsTable.tsx`** — 목록 테이블 + 페이지네이션 JSX(기존 435줄 파일의 `<table>`+`<nav>` 블록)를 그대로 이동. `rows`/`isAnyPending`/`openTransition`/`triggerRefresh`/`pagination`을 props로 받음. test-id(`shipping-table`, `shipping-row-{i}`, `shipping-row-status-{i}`, `shipping-transition-{i}`, `shipping-refresh-{i}`, `shipping-prev`, `shipping-pageinfo`, `shipping-next`) 전부 보존.
- **`src/features/ecommerce-ops/components/ShippingsScreen.tsx`** — 훅 + 테이블로 슬림화. 남는 마크업(헤딩·필터 폼·forbidden/degraded/loading/empty 분기·2 다이얼로그) 유지. `ShippingsScreenProps` 공개 시그니처 불변.
- **Tests** — `src/features/ecommerce-ops/**` + `tests/unit/**`의 기존 vitest(특히 `ecommerce-shippings-nav` 및 `ShipFormDialog`)가 **무변경으로 green**이어야 한다. barrel `@/features/ecommerce-ops`의 `ShippingsScreen` export 경로·시그니처 불변이므로 import-site 수정 불필요.

## Out of Scope

- `ShipFormDialog` 내부 변경(carrier/trackingNumber/WMS-deduct 토글) — 본 분할은 호출부만 이동.
- 상태머신(`allowedNextStatus`)·degrade/forbidden 조건·wire body의 **동작 변경**(순수 이동만).
- `ShippingsScreen` 공개 props/배럴 export 표면 변경.
- 다른 ecommerce-ops 화면(Orders/Promotions 등) 분할 — 별건(필요 시 후속 PC-FE).
- 백엔드/계약/스펙/배송 API 변경.

# Acceptance Criteria

- [ ] `useShippingsScreen`/`ShippingsTable`/`shipping-labels` 추출 후 `ShippingsScreen`은 훅+테이블 배선만 남고, 렌더 출력·DOM·모든 `data-testid`가 기존과 동일하다.
- [ ] 목록/필터: 상태 필터 제출 시 page 0 재조회, seeded(page 0·무필터) 진입만 서버렌더 seed fallback, 필터/페이지 진입은 로딩 플레이스홀더 동작이 불변.
- [ ] 분기: forbidden(403)→`shipping-forbidden`, degrade(≥500/네트워크)→`shipping-degraded`, loading→`shipping-loading`, empty→`shipping-empty`, 그 외 테이블 렌더가 기존과 동일.
- [ ] 상태 전이: 비-SHIPPED 전이는 ConfirmDialog(confirm 시 PATCH `{status}`), PREPARING→SHIPPED는 ShipFormDialog(carrier+trackingNumber, `deductWmsInventory`는 true일 때만 wire 포함), 추적 동기화는 best-effort mutate. `isAnyPending` 공유 가드가 모든 행 버튼을 동일하게 비활성화한다.
- [ ] 페이지네이션: prev/next disabled 조건·pageInfo 문자열·onPrev/onNext setQuery 동작이 불변.
- [ ] `pnpm exec vitest run` green(무회귀), `npx tsc --noEmit` clean, `pnpm lint` clean(no-unused-vars 등 CI 두 프런트 잡 가드 — `env_console_web_local_verify_needs_lint`). scope = console-web only.

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.10.3(배송 운영) — read-only 소비, 변경 없음(동일 호출·동일 wire shape).
- `projects/platform-console/specs/services/console-web/architecture.md` § Server vs Client Components / § 2.5 Resilience — 소비만, 변경 없음(클라이언트 컴포넌트 내부 구조 정리, degrade-only 분기 유지).

# Related Contracts

- 변경 없음. 동일 배송 목록/상태전이/추적 API·동일 클라이언트 훅(`useShippings`/`useUpdateShippingStatus`/`useRefreshTracking`)·동일 PATCH body.

# Target Service

- `platform-console` / `apps/console-web` — `src/features/ecommerce-ops/components/{ShippingsScreen,ShippingsTable}.tsx` + `use-shippings-screen.ts` + `shipping-labels.ts`. behavior-preserving 컨테이너/프레젠테이셔널 + 커스텀 훅 분할.

# Architecture

- React 컨테이너/프레젠테이셔널 + 커스텀 훅 추출 패턴(PC-FE 분할 시리즈 PC-FE-098~112 / PC-FE-101 `ApprovalScreen` / PC-FE-106 `useLedgerOpsState`와 동일 계열). fat `'use client'` 컴포넌트 → (1) 상태·쿼리·뮤테이션 훅, (2) 표현 전용 테이블, (3) 순수 라벨 상수, (4) 얇은 배선 컨테이너. 상태 소유권은 훅(부모)에 유지해 테이블 추출이 데이터 흐름·pending 가드에 영향을 주지 않음. RSC 경계·렌더 트리 불변.

# Edge Cases

- seeded(page 0·무필터) 진입: 서버렌더 `shippings` seed fallback. 필터/페이지 이동 후에는 seed 미사용 → 로딩 플레이스홀더(전체 미필터 목록 깜빡임 방지) — 훅의 `seeded`/`data` 파생 그대로 이동.
- 행 작업 게이팅: `isAnyPending`(updateStatus || refreshTracking)로 전이·추적 버튼 동시 비활성 — 테이블에 prop 전달, 동작 불변.
- PREPARING→SHIPPED: ShipFormDialog 경유(carrier+trackingNumber 필수), `deductWmsInventory`는 true일 때만 body 포함(false는 wire 제외, producer 기본값) — 훅 `confirmShip` 매핑 불변.
- 전이 가능 상태 없음(`allowedNextStatus`===null): 전이 버튼 미렌더. PREPARING/DELIVERED 행: 추적 동기화 버튼 미렌더 — 테이블 조건부 렌더 그대로.
- 페이지네이션: prev는 `query.page`, next/pageInfo는 `data.page` 기준(서버 확정 페이지) — 훅 pagination 파생이 두 소스 구분 유지.
- 403 vs ≥500: forbidden은 inline actionable, degrade는 section-only(콘솔 나머지 정상) — 분기 우선순위(forbidden → degraded → loading → empty → table) 그대로.

# Failure Scenarios

- 추출 과정에서 test-id 오타/누락 → `ecommerce-shippings-nav` 등 기존 테스트 RED 또는 e2e 셀렉터 깨짐: AC로 test-id 전수 보존 가드, `vitest run`으로 회귀 확인.
- 상태를 테이블로 내리면(props-lift 누락) 행 작업/페이지 이동 시 상태 리셋 → 상태는 훅(부모)에 유지, 테이블은 표현 전용.
- `deductWmsInventory` 매핑이 추출 중 바뀌어 false도 wire에 실리면 producer 계약/최소 body 관례 위반 → 순수 이동(`...(true ? {deductWmsInventory:true} : {})`), AC로 가드.
- seeded fallback 조건이 바뀌면 필터/페이지 진입 시 미필터 목록 깜빡임 회귀 → `seeded` 파생 그대로 이동, Edge Case로 가드.
- 잔여 미사용 import(useId/useState/ApiError 등 컨테이너에서 제거) → `pnpm lint` no-unused-vars RED: push 전 lint+tsc 필수(가드 AC).
- 페이지네이션 disabled가 단일 소스로 합쳐지면 경계 동작 변화 → prev=`query.page`, next=`data.page` 두 소스 구분 유지.

# Definition of Done

- [ ] `shipping-labels.ts`(라벨/필터옵션) + `use-shippings-screen.ts`(상태·쿼리·뮤테이션·페이지네이션) + `ShippingsTable.tsx`(테이블+페이지네이션) 추출, `ShippingsScreen.tsx`는 배선만
- [ ] 렌더 출력·DOM·전체 `data-testid`·상태머신/degrade/forbidden/loading/empty 분기·confirm/ShipFormDialog 분기·wire body·페이지네이션 behavior-preserving
- [ ] 기존 vitest(ecommerce-shippings-nav / ShipFormDialog 포함) 무변경 green + tsc + lint clean, 무회귀; scope = console-web only
- [ ] Acceptance Criteria 충족
- [ ] Ready for review
